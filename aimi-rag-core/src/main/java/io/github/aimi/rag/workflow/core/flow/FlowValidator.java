package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowValidationException;
import io.github.aimi.rag.workflow.core.step.ParallelEndStep;
import io.github.aimi.rag.workflow.core.step.ParallelStartStep;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedPseudograph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Validates flow graph structure: orphan nodes, condition conflicts,
 * parallel branch exclusivity, and illegal cycles.
 */
public class FlowValidator {

    private final Graph<FlowNode, FlowEdge> graph;
    private final FlowNode startNode;
    private final FlowNode endNode;

    public FlowValidator(Graph<FlowNode, FlowEdge> graph, FlowNode startNode, FlowNode endNode) {
        this.graph = graph;
        this.startNode = startNode;
        this.endNode = endNode;
    }

    /**
     * Run all validations. Throws FlowValidationException on first failure.
     */
    public void validate() {
        validateEndReachable();
        validateNoOrphanNodes();
        validateConditionConflicts();
        validateParallelBranches();
        validateNoIllegalCycles();
    }

    /**
     * Task 5.2: Orphan node detection.
     * BFS from START, any vertex not reachable is an orphan.
     */
    private void validateNoOrphanNodes() {
        if (startNode == null) {
            throw new FlowValidationException("Start node is missing");
        }

        Set<FlowNode> reachable = new HashSet<>();
        Queue<FlowNode> queue = new LinkedList<>();
        queue.add(startNode);
        reachable.add(startNode);

        while (!queue.isEmpty()) {
            FlowNode current = queue.poll();
            Set<FlowEdge> outgoing = graph.outgoingEdgesOf(current);
            for (FlowEdge edge : outgoing) {
                FlowNode target = graph.getEdgeTarget(edge);
                if (!reachable.contains(target)) {
                    reachable.add(target);
                    queue.add(target);
                }
            }
        }

        Set<FlowNode> allVertices = graph.vertexSet();
        List<String> orphans = new ArrayList<>();
        for (FlowNode node : allVertices) {
            if (!reachable.contains(node)) {
                orphans.add(node.getName());
            }
        }

        if (!orphans.isEmpty()) {
            throw new FlowValidationException("Orphan nodes not reachable from START: " + orphans);
        }
    }

    /**
     * END node must be reachable from START.
     */
    private void validateEndReachable() {
        if (endNode == null) {
            throw new FlowValidationException("End node is missing");
        }
        if (startNode == null) {
            throw new FlowValidationException("Start node is missing");
        }

        Set<FlowNode> visited = new HashSet<>();
        Queue<FlowNode> queue = new LinkedList<>();
        queue.add(startNode);
        visited.add(startNode);

        boolean endFound = false;
        while (!queue.isEmpty()) {
            FlowNode current = queue.poll();
            if (current.equals(endNode)) {
                endFound = true;
                break;
            }
            for (FlowEdge edge : graph.outgoingEdgesOf(current)) {
                FlowNode target = graph.getEdgeTarget(edge);
                if (!visited.contains(target)) {
                    visited.add(target);
                    queue.add(target);
                }
            }
        }

        if (!endFound) {
            throw new FlowValidationException("END node is not reachable from START");
        }
    }

    /**
     * Task 5.3: Condition conflict detection.
     * Each decider node must not have duplicate status on outgoing edges.
     * Wildcard "*" is allowed only once per decider.
     */
    private void validateConditionConflicts() {
        for (FlowNode node : graph.vertexSet()) {
            if (!node.isDecider()) {
                continue;
            }

            Set<FlowEdge> outgoing = graph.outgoingEdgesOf(node);
            Map<String, Integer> statusCount = new HashMap<>();

            for (FlowEdge edge : outgoing) {
                String status = edge.getStatus();
                if (status == null) {
                    status = FlowEdge.WILDCARD;
                }
                statusCount.merge(status, 1, Integer::sum);
            }

            List<String> conflicts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : statusCount.entrySet()) {
                if (entry.getValue() > 1) {
                    conflicts.add("'" + entry.getKey() + "' x" + entry.getValue());
                }
            }

            if (!conflicts.isEmpty()) {
                throw new FlowValidationException(
                        "Decider '" + node.getName() + "' has duplicate conditions: " + conflicts);
            }
        }
    }

    /**
     * Task 5.4: Parallel branch exclusivity detection.
     * - Same Step must not appear in multiple parallel branches.
     * - Nodes inside a parallel region must not connect to nodes outside the region
     *   (except via ParallelStartStep entries and ParallelEndStep exits).
     */
    private void validateParallelBranches() {
        List<FlowNode> parallelStarts = new ArrayList<>();
        for (FlowNode node : graph.vertexSet()) {
            if (isParallelStart(node)) {
                parallelStarts.add(node);
            }
        }

        for (FlowNode parallelStart : parallelStarts) {
            validateSingleParallelRegion(parallelStart);
        }
    }

    private void validateSingleParallelRegion(FlowNode parallelStart) {
        FlowNode parallelEnd = findParallelEndFromStart(parallelStart);
        if (parallelEnd == null) {
            throw new FlowValidationException(
                    "Parallel region starting at '" + parallelStart.getName() + "' has no matching PARALLEL_END");
        }

        Set<FlowNode> branchStarts = new LinkedHashSet<>();
        for (FlowEdge edge : graph.outgoingEdgesOf(parallelStart)) {
            FlowNode target = graph.getEdgeTarget(edge);
            if (!target.equals(parallelEnd)) {
                branchStarts.add(target);
            }
        }

        if (branchStarts.isEmpty()) {
            throw new FlowValidationException(
                    "Parallel region at '" + parallelStart.getName() + "' has no branches");
        }

        Map<FlowNode, List<String>> stepToBranches = new HashMap<>();
        int branchIndex = 0;

        for (FlowNode branchStart : branchStarts) {
            branchIndex++;
            String branchLabel = "branch-" + branchIndex;
            Set<FlowNode> visited = new HashSet<>();
            Queue<FlowNode> queue = new LinkedList<>();
            queue.add(branchStart);

            while (!queue.isEmpty()) {
                FlowNode current = queue.poll();
                if (visited.contains(current)) {
                    continue;
                }
                visited.add(current);

                if (current.equals(parallelEnd)) {
                    continue;
                }

                if (isParallelStart(current) && !current.equals(parallelStart)) {
                    throw new FlowValidationException(
                            "Nested parallel region detected at '" + current.getName() +
                                    "' inside parallel region starting at '" + parallelStart.getName() + "'");
                }

                if (current.isStep() && !isParallelMarker(current)) {
                    stepToBranches.computeIfAbsent(current, k -> new ArrayList<>()).add(branchLabel);
                }

                for (FlowEdge edge : graph.outgoingEdgesOf(current)) {
                    FlowNode target = graph.getEdgeTarget(edge);
                    if (target.equals(parallelEnd)) {
                        continue;
                    }
                    if (!isWithinRegion(target, parallelStart, parallelEnd)) {
                        throw new FlowValidationException(
                                "Node '" + current.getName() + "' inside parallel region connects to '" +
                                        target.getName() + "' which is outside the region");
                    }
                    queue.add(target);
                }
            }
        }

        List<String> sharedSteps = new ArrayList<>();
        for (Map.Entry<FlowNode, List<String>> entry : stepToBranches.entrySet()) {
            if (entry.getValue().size() > 1) {
                sharedSteps.add(entry.getKey().getName() + " (in " + entry.getValue() + ")");
            }
        }
        if (!sharedSteps.isEmpty()) {
            throw new FlowValidationException(
                    "Step appears in multiple parallel branches: " + sharedSteps);
        }
    }

    private boolean isWithinRegion(FlowNode node, FlowNode regionStart, FlowNode regionEnd) {
        Set<FlowNode> visited = new HashSet<>();
        Queue<FlowNode> queue = new LinkedList<>();
        queue.add(regionStart);
        visited.add(regionStart);

        while (!queue.isEmpty()) {
            FlowNode current = queue.poll();
            if (current.equals(node)) {
                return true;
            }
            if (current.equals(regionEnd) && !current.equals(regionStart)) {
                continue;
            }
            for (FlowEdge edge : graph.outgoingEdgesOf(current)) {
                FlowNode target = graph.getEdgeTarget(edge);
                if (!visited.contains(target)) {
                    visited.add(target);
                    queue.add(target);
                }
            }
        }
        return false;
    }

    private FlowNode findParallelEndFromStart(FlowNode parallelStart) {
        Set<FlowNode> visited = new HashSet<>();
        Queue<FlowNode> queue = new LinkedList<>();
        queue.add(parallelStart);
        visited.add(parallelStart);

        while (!queue.isEmpty()) {
            FlowNode current = queue.poll();
            if (isParallelEnd(current) && !current.equals(parallelStart)) {
                return current;
            }
            for (FlowEdge edge : graph.outgoingEdgesOf(current)) {
                FlowNode target = graph.getEdgeTarget(edge);
                if (!visited.contains(target)) {
                    visited.add(target);
                    queue.add(target);
                }
            }
        }
        return null;
    }

    /**
     * Task 5.5: Illegal cycle detection.
     * Detects cycles formed by non-LOOP edges. LOOP edges are legitimate
     * back-edges created by the loop()/endLoop() DSL and are excluded.
     */
    private void validateNoIllegalCycles() {
        Graph<FlowNode, FlowEdge> nonLoopGraph = new DirectedPseudograph<>(FlowEdge.class);
        for (FlowNode node : graph.vertexSet()) {
            nonLoopGraph.addVertex(node);
        }
        for (FlowEdge edge : graph.edgeSet()) {
            if ("LOOP".equals(edge.getStatus())) {
                continue;
            }
            FlowNode source = graph.getEdgeSource(edge);
            FlowNode target = graph.getEdgeTarget(edge);
            nonLoopGraph.addEdge(source, target, edge);
        }

        CycleDetector<FlowNode, FlowEdge> detector = new CycleDetector<>(nonLoopGraph);
        Set<FlowNode> cycleNodes = detector.findCycles();

        if (!cycleNodes.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (FlowNode node : cycleNodes) {
                names.add(node.getName());
            }
            throw new FlowValidationException(
                    "Illegal cycle detected (not created by loop()/endLoop()): " + names);
        }
    }

    private boolean isParallelStart(FlowNode node) {
        return node.isStep() && node.getStep() instanceof ParallelStartStep;
    }

    private boolean isParallelEnd(FlowNode node) {
        return node.isStep() && node.getStep() instanceof ParallelEndStep;
    }

    private boolean isParallelMarker(FlowNode node) {
        return isParallelStart(node) || isParallelEnd(node);
    }
}
