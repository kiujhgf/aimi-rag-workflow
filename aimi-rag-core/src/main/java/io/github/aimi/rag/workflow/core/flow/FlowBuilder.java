package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowValidationException;
import io.github.aimi.rag.workflow.core.model.FlowDecider;
import io.github.aimi.rag.workflow.core.step.EndStep;
import io.github.aimi.rag.workflow.core.step.ParallelEndStep;
import io.github.aimi.rag.workflow.core.step.ParallelStartStep;
import io.github.aimi.rag.workflow.core.step.StartStep;
import io.github.aimi.rag.workflow.core.step.Step;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class FlowBuilder {

    private final Graph<FlowNode, FlowEdge> graph;
    private final Map<Step<?, ?>, FlowNode> stepNodeMap;
    private final Map<String, FlowNode> deciderNodeMap;

    private FlowNode startNode;
    private FlowNode endNode;
    private FlowNode currentNode;

    private boolean startAdded = false;
    private boolean endAdded = false;

    private boolean inLoop = false;
    private FlowDecider loopDecider;
    private FlowNode loopFirstStepNode;
    private LoopConfig loopConfig;

    private boolean inParallel = false;
    private FlowNode parallelStartNode;
    private FlowNode parallelEndNode;
    private final List<FlowNode> parallelBranchLastNodes = new ArrayList<>();
    private FlowNode currentBranchFirstNode;
    private boolean newBranchStarted = false;
    private ContextMerger parallelMerger;

    private FlowBuilder() {
        this.graph = new DirectedPseudograph<>(FlowEdge.class);
        this.stepNodeMap = new HashMap<>();
        this.deciderNodeMap = new HashMap<>();
    }

    public static FlowBuilder create() {
        return new FlowBuilder();
    }

    public FlowBuilder start() {
        if (startAdded) {
            throw new FlowValidationException("Start node already added");
        }
        startNode = new FlowNode(StartStep.INSTANCE);
        graph.addVertex(startNode);
        stepNodeMap.put(StartStep.INSTANCE, startNode);
        currentNode = startNode;
        startAdded = true;
        return this;
    }

    public FlowBuilder end() {
        if (endAdded) {
            throw new FlowValidationException("End node already added");
        }
        endNode = new FlowNode(EndStep.INSTANCE);
        graph.addVertex(endNode);
        stepNodeMap.put(EndStep.INSTANCE, endNode);
        if (currentNode != null) {
            graph.addEdge(currentNode, endNode, new FlowEdge());
        }
        endAdded = true;
        return this;
    }

    public FlowBuilder step(Step<?, ?> step) {
        ensureStarted();

        FlowNode node = getOrCreateStepNode(step);

        if (inParallel && newBranchStarted) {
            graph.addEdge(parallelStartNode, node, new FlowEdge());
            currentBranchFirstNode = node;
            newBranchStarted = false;
        } else if (currentNode != null) {
            graph.addEdge(currentNode, node, new FlowEdge());
        }

        if (inParallel && currentBranchFirstNode == null) {
            currentBranchFirstNode = node;
        }

        if (inLoop && loopFirstStepNode == null) {
            loopFirstStepNode = node;
        }

        currentNode = node;
        return this;
    }

    public FlowBuilder decider(FlowDecider decider, String name) {
        ensureStarted();

        FlowNode node = getOrCreateDeciderNode(decider, name);

        if (inParallel && newBranchStarted) {
            graph.addEdge(parallelStartNode, node, new FlowEdge());
            currentBranchFirstNode = node;
            newBranchStarted = false;
        } else if (currentNode != null) {
            graph.addEdge(currentNode, node, new FlowEdge());
        }

        currentNode = node;
        return this;
    }

    public TransitionBuilder on(String status) {
        if (currentNode == null || !currentNode.isDecider()) {
            throw new FlowValidationException("on() can only be called after decider()");
        }
        return new TransitionBuilder(this, currentNode, status);
    }

    public FlowBuilder from(Step<?, ?> step) {
        FlowNode node = stepNodeMap.get(step);
        if (node == null) {
            throw new FlowValidationException("Step not found in flow: " + step.getName());
        }
        currentNode = node;
        return this;
    }

    public FlowBuilder from(FlowDecider decider, String name) {
        FlowNode node = deciderNodeMap.get(name);
        if (node == null) {
            throw new FlowValidationException("Decider not found in flow: " + name);
        }
        currentNode = node;
        return this;
    }

    public FlowBuilder loop(FlowDecider decider) {
        return loop(decider, LoopConfig.defaultConfig());
    }

    public FlowBuilder loop(FlowDecider decider, LoopConfig config) {
        if (inLoop) {
            throw new FlowValidationException("Cannot nest loops");
        }
        ensureStarted();

        inLoop = true;
        loopConfig = config;
        loopDecider = decider;
        loopFirstStepNode = null;

        return this;
    }

    public FlowBuilder endLoop() {
        if (!inLoop) {
            throw new FlowValidationException("endLoop() called without loop()");
        }
        if (loopFirstStepNode == null) {
            throw new FlowValidationException("Loop body must contain at least one step");
        }

        FlowNode deciderNode = getOrCreateDeciderNode(loopDecider, "loop-decider-" + System.identityHashCode(loopDecider), loopConfig);

        if (currentNode != null) {
            graph.addEdge(currentNode, deciderNode, new FlowEdge());
        }

        graph.addEdge(deciderNode, loopFirstStepNode, new FlowEdge("LOOP"));

        currentNode = deciderNode;

        inLoop = false;
        loopDecider = null;
        loopFirstStepNode = null;
        loopConfig = null;

        return this;
    }

    public FlowBuilder parallel() {
        return parallel(null, null);
    }

    public FlowBuilder parallel(ExecutorService executor) {
        return parallel(executor, null);
    }

    public FlowBuilder parallel(ExecutorService executor, ContextMerger merger) {
        if (inParallel) {
            throw new FlowValidationException("Nested parallel groups are not supported");
        }
        ensureStarted();

        inParallel = true;
        parallelMerger = merger;
        parallelStartNode = new FlowNode(new ParallelStartStep(executor));
        graph.addVertex(parallelStartNode);

        if (currentNode != null) {
            graph.addEdge(currentNode, parallelStartNode, new FlowEdge());
        }

        parallelBranchLastNodes.clear();
        newBranchStarted = true;
        currentNode = null;

        return this;
    }

    public FlowBuilder on() {
        if (!inParallel) {
            throw new FlowValidationException("on() can only be called inside parallel()");
        }

        if (currentBranchFirstNode != null) {
            parallelBranchLastNodes.add(currentNode);
        }

        newBranchStarted = true;
        currentNode = null;
        currentBranchFirstNode = null;

        return this;
    }

    public FlowBuilder endParallel() {
        if (!inParallel) {
            throw new FlowValidationException("endParallel() called without parallel()");
        }

        if (currentBranchFirstNode != null) {
            parallelBranchLastNodes.add(currentNode);
        }

        parallelEndNode = new FlowNode(new ParallelEndStep(parallelMerger));
        graph.addVertex(parallelEndNode);

        for (FlowNode lastNode : parallelBranchLastNodes) {
            graph.addEdge(lastNode, parallelEndNode, new FlowEdge());
        }

        currentNode = parallelEndNode;
        inParallel = false;
        parallelStartNode = null;
        parallelEndNode = null;
        parallelBranchLastNodes.clear();
        currentBranchFirstNode = null;
        newBranchStarted = false;
        parallelMerger = null;

        return this;
    }

    public Flow build() {
        validateSyntax();

        if (!startAdded) {
            start();
        }
        if (!endAdded) {
            if (currentNode != null && !currentNode.equals(endNode)) {
                end();
            }
        }

        Flow flow = new Flow(graph, startNode, endNode);
        new FlowValidator(graph, startNode, endNode).validate();
        return flow;
    }

    /**
     * Task 5.1: Syntax validation. Ensures loop() and parallel() blocks are closed.
     */
    private void validateSyntax() {
        if (inLoop) {
            throw new FlowValidationException("Loop not closed with endLoop()");
        }
        if (inParallel) {
            throw new FlowValidationException("Parallel not closed with endParallel()");
        }
    }

    private void ensureStarted() {
        if (!startAdded) {
            start();
        }
    }

    private FlowNode getOrCreateStepNode(Step<?, ?> step) {
        FlowNode node = stepNodeMap.get(step);
        if (node == null) {
            node = new FlowNode(step);
            graph.addVertex(node);
            stepNodeMap.put(step, node);
        }
        return node;
    }

    private FlowNode getOrCreateDeciderNode(FlowDecider decider, String name) {
        return getOrCreateDeciderNode(decider, name, null);
    }

    private FlowNode getOrCreateDeciderNode(FlowDecider decider, String name, LoopConfig loopConfig) {
        FlowNode node = deciderNodeMap.get(name);
        if (node == null) {
            node = new FlowNode(decider, name, loopConfig);
            graph.addVertex(node);
            deciderNodeMap.put(name, node);
        }
        return node;
    }

    FlowNode getOrCreateStepNodeForTransition(Step<?, ?> step) {
        if (step == Flow.END_STEP) {
            if (endNode == null) {
                endNode = new FlowNode(EndStep.INSTANCE);
                graph.addVertex(endNode);
                stepNodeMap.put(EndStep.INSTANCE, endNode);
                endAdded = true;
            }
            return endNode;
        }
        if (step == Flow.START_STEP) {
            return startNode;
        }
        return getOrCreateStepNode(step);
    }

    void addTransition(FlowNode source, FlowNode target, String status) {
        graph.addEdge(source, target, new FlowEdge(status));
    }

    public class TransitionBuilder {

        private final FlowBuilder builder;
        private final FlowNode deciderNode;
        private final String status;

        TransitionBuilder(FlowBuilder builder, FlowNode deciderNode, String status) {
            this.builder = builder;
            this.deciderNode = deciderNode;
            this.status = status;
        }

        public FlowBuilder to(Step<?, ?> step) {
            FlowNode target = builder.getOrCreateStepNodeForTransition(step);
            builder.addTransition(deciderNode, target, status);
            return builder;
        }

        public FlowBuilder to(FlowDecider decider, String name) {
            FlowNode target = builder.getOrCreateDeciderNode(decider, name);
            builder.addTransition(deciderNode, target, status);
            return builder;
        }
    }
}
