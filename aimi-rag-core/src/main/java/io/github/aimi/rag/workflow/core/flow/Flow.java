package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.step.EndStep;
import io.github.aimi.rag.workflow.core.step.ParallelEndStep;
import io.github.aimi.rag.workflow.core.step.ParallelStartStep;
import io.github.aimi.rag.workflow.core.step.StartStep;
import io.github.aimi.rag.workflow.core.step.Step;
import org.jgrapht.Graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class Flow {

    public static final Step<?, ?> START_STEP = StartStep.INSTANCE;
    public static final Step<?, ?> END_STEP = EndStep.INSTANCE;

    private static final int DEFAULT_LOOP_MAX_ITERATIONS = 20;

    private final Graph<FlowNode, FlowEdge> graph;
    private final FlowNode startNode;
    private final FlowNode endNode;
    private final List<FlowExecutionListener> listeners = new ArrayList<>();

    public Flow(Graph<FlowNode, FlowEdge> graph, FlowNode startNode, FlowNode endNode) {
        this.graph = graph;
        this.startNode = startNode;
        this.endNode = endNode;
    }

    FlowNode getStartNode() {
        return startNode;
    }

    FlowNode getEndNode() {
        return endNode;
    }

    Graph<FlowNode, FlowEdge> getGraph() {
        return graph;
    }

    public void addListener(FlowExecutionListener listener) {
        listeners.add(listener);
    }

    public List<FlowExecutionListener> getListeners() {
        return listeners;
    }

    public FlowContext execute(FlowContext context) {
        if (context == null) {
            context = FlowContext.create();
        }

        notifyFlowStart(context);

        try {
            FlowNode current = startNode;
            Map<String, Integer> loopCounters = new HashMap<>();

            while (current != null) {
                if (current.equals(endNode)) {
                    break;
                }

                String stepName = current.getName();
                notifyStepStart(stepName, context);

                try {
                    if (isParallelStart(current)) {
                        current = executeParallel(current, context);
                        continue;
                    }

                    StepStatus status;
                    String deciderResult = null;

                    if (current.isDecider()) {
                        deciderResult = current.decide(context);
                        status = StepStatus.FINISHED;
                    } else {
                        status = current.execute(context);
                    }

                    notifyStepSuccess(stepName, status, context);

                    if (status == StepStatus.FAILED) {
                        FlowException ex = new FlowException("Flow failed at step: " + stepName);
                        notifyStepFailure(stepName, ex, context);
                        throw ex;
                    }

                    if (status == StepStatus.END) {
                        current = endNode;
                        continue;
                    }

                    FlowNode next = findNextNode(current, deciderResult);

                    if (next != null && isLoopBack(current, next)) {
                        Integer count = loopCounters.merge(current.getName(), 1, Integer::sum);
                        int maxIterations = current.getLoopConfig() != null 
                                ? current.getLoopConfig().getMaxIterations() 
                                : DEFAULT_LOOP_MAX_ITERATIONS;
                        if (count > maxIterations) {
                            FlowException ex = new FlowException("Loop exceeded max iterations (" + maxIterations + ") at: " + current.getName());
                            notifyStepFailure(stepName, ex, context);
                            throw ex;
                        }
                    }

                    current = next;

                } catch (FlowException e) {
                    notifyStepFailure(stepName, e, context);
                    throw e;
                } catch (Exception e) {
                    FlowException flowEx = new FlowException("Unexpected error at step: " + stepName, e);
                    notifyStepFailure(stepName, flowEx, context);
                    throw flowEx;
                }
            }

            notifyFlowComplete(context);
            return context;

        } catch (FlowException e) {
            notifyFlowFailure(e, context);
            throw e;
        }
    }

    private boolean isParallelStart(FlowNode node) {
        return node.isStep() && node.getStep() instanceof ParallelStartStep;
    }

    private boolean isParallelEnd(FlowNode node) {
        return node.isStep() && node.getStep() instanceof ParallelEndStep;
    }

    private boolean isLoopBack(FlowNode from, FlowNode to) {
        Set<FlowEdge> edges = graph.getAllEdges(from, to);
        for (FlowEdge edge : edges) {
            if ("LOOP".equals(edge.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private FlowNode findNextNode(FlowNode current, String deciderResult) {
        Set<FlowEdge> outgoingEdges = graph.outgoingEdgesOf(current);

        if (outgoingEdges.isEmpty()) {
            return null;
        }

        if (deciderResult != null) {
            for (FlowEdge edge : outgoingEdges) {
                if (deciderResult.equals(edge.getStatus())) {
                    return graph.getEdgeTarget(edge);
                }
            }
        }

        for (FlowEdge edge : outgoingEdges) {
            if (edge.isWildcard()) {
                return graph.getEdgeTarget(edge);
            }
        }

        return null;
    }

    private FlowNode executeParallel(FlowNode parallelStartNode, FlowContext mainContext) {
        ParallelStartStep parallelStartStep = (ParallelStartStep) parallelStartNode.getStep();

        parallelStartNode.execute(mainContext);

        ExecutorService executor = parallelStartStep.getExecutorService();
        if (executor == null) {
            throw new FlowException("Parallel executor is not available");
        }

        try {
            Set<FlowEdge> outgoingEdges = graph.outgoingEdgesOf(parallelStartNode);
            List<FlowNode> branchStarts = new ArrayList<>();

            for (FlowEdge edge : outgoingEdges) {
                branchStarts.add(graph.getEdgeTarget(edge));
            }

            List<CompletableFuture<FlowContext>> futures = new ArrayList<>();

            for (FlowNode branchStart : branchStarts) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    FlowContext subContext = FlowContext.create();
                    executeBranch(branchStart, subContext);
                    return subContext;
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            FlowNode parallelEndNode = null;
            for (FlowNode branchStart : branchStarts) {
                FlowNode endOfBranch = findParallelEnd(branchStart);
                if (endOfBranch != null) {
                    parallelEndNode = endOfBranch;
                    break;
                }
            }

            if (parallelEndNode == null) {
                throw new FlowException("Parallel end node not found");
            }

            ParallelEndStep parallelEndStep = (ParallelEndStep) parallelEndNode.getStep();
            parallelEndStep.clearSubContexts();
            for (CompletableFuture<FlowContext> future : futures) {
                parallelEndStep.addSubContext(future.join());
            }

            parallelEndNode.execute(mainContext);

            FlowNode afterParallel = findNextNode(parallelEndNode, null);
            return afterParallel;

        } finally {
            parallelStartStep.shutdownExecutor();
        }
    }

    private void executeBranch(FlowNode startNode, FlowContext context) {
        FlowNode current = startNode;

        while (current != null) {
            if (isParallelEnd(current)) {
                break;
            }

            if (current.isDecider()) {
                String result = current.decide(context);
                current = findNextNode(current, result);
            } else {
                StepStatus status = current.execute(context);
                if (status == StepStatus.FAILED) {
                    throw new FlowException("Branch failed at step: " + current.getName());
                }
                if (status == StepStatus.END) {
                    break;
                }
                current = findNextNode(current, null);
            }
        }
    }

    private FlowNode findParallelEnd(FlowNode startNode) {
        FlowNode current = startNode;
        java.util.Set<FlowNode> visited = new java.util.HashSet<>();

        while (current != null && !visited.contains(current)) {
            if (isParallelEnd(current)) {
                return current;
            }
            visited.add(current);
            current = findNextNode(current, null);
        }

        return null;
    }

    private void notifyFlowStart(FlowContext context) {
        for (FlowExecutionListener listener : listeners) {
            listener.onFlowStart(context);
        }
    }

    private void notifyStepStart(String stepName, FlowContext context) {
        for (FlowExecutionListener listener : listeners) {
            listener.onStepStart(stepName, context);
        }
    }

    private void notifyStepSuccess(String stepName, StepStatus status, FlowContext context) {
        for (FlowExecutionListener listener : listeners) {
            listener.onStepSuccess(stepName, status, context);
        }
    }

    private void notifyStepFailure(String stepName, Throwable error, FlowContext context) {
        for (FlowExecutionListener listener : listeners) {
            listener.onStepFailure(stepName, error, context);
        }
    }

    private void notifyFlowComplete(FlowContext context) {
        for (FlowExecutionListener listener : listeners) {
            listener.onFlowComplete(context);
        }
    }

    private void notifyFlowFailure(Throwable error, FlowContext context) {
        for (FlowExecutionListener listener : listeners) {
            listener.onFlowFailure(error, context);
        }
    }
}
