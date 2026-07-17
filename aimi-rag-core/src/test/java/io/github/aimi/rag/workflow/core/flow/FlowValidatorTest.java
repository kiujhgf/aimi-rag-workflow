package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowValidationException;
import io.github.aimi.rag.workflow.core.flow.TestSteps.SimpleStep;
import io.github.aimi.rag.workflow.core.model.FlowDecider;
import io.github.aimi.rag.workflow.core.step.EndStep;
import io.github.aimi.rag.workflow.core.step.ParallelEndStep;
import io.github.aimi.rag.workflow.core.step.ParallelStartStep;
import io.github.aimi.rag.workflow.core.step.StartStep;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowValidatorTest {

    @Test
    void shouldPassValidSequentialFlow() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode step1 = node(graph, new SimpleStep("step1"));

        graph.addEdge(start, step1, new FlowEdge());
        graph.addEdge(step1, end, new FlowEdge());

        assertDoesNotThrow(() -> new FlowValidator(graph, start, end).validate());
    }

    @Test
    void shouldDetectOrphanNode() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode step1 = node(graph, new SimpleStep("step1"));
        FlowNode orphan = node(graph, new SimpleStep("orphan"));

        graph.addEdge(start, step1, new FlowEdge());
        graph.addEdge(step1, end, new FlowEdge());

        FlowValidationException ex = assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, end).validate());
        assertTrue(ex.getMessage().contains("Orphan"));
        assertTrue(ex.getMessage().contains("orphan"));
    }

    @Test
    void shouldDetectUnreachableEnd() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode step1 = node(graph, new SimpleStep("step1"));

        graph.addEdge(start, step1, new FlowEdge());

        FlowValidationException ex = assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, end).validate());
        assertTrue(ex.getMessage().contains("END"));
    }

    @Test
    void shouldDetectConditionConflict() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode decider = deciderNode(graph, "route");
        FlowNode branch1 = node(graph, new SimpleStep("branch1"));
        FlowNode branch2 = node(graph, new SimpleStep("branch2"));

        graph.addEdge(start, decider, new FlowEdge());
        graph.addEdge(decider, branch1, new FlowEdge("A"));
        graph.addEdge(decider, branch2, new FlowEdge("A"));
        graph.addEdge(branch1, end, new FlowEdge());
        graph.addEdge(branch2, end, new FlowEdge());

        FlowValidationException ex = assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, end).validate());
        assertTrue(ex.getMessage().contains("duplicate conditions"));
        assertTrue(ex.getMessage().contains("route"));
    }

    @Test
    void shouldAllowWildcardAndSpecificConditions() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode decider = deciderNode(graph, "route");
        FlowNode branchA = node(graph, new SimpleStep("branchA"));
        FlowNode defaultBranch = node(graph, new SimpleStep("default"));

        graph.addEdge(start, decider, new FlowEdge());
        graph.addEdge(decider, branchA, new FlowEdge("A"));
        graph.addEdge(decider, defaultBranch, new FlowEdge("*"));
        graph.addEdge(branchA, end, new FlowEdge());
        graph.addEdge(defaultBranch, end, new FlowEdge());

        assertDoesNotThrow(() -> new FlowValidator(graph, start, end).validate());
    }

    @Test
    void shouldDetectDuplicateWildcard() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode decider = deciderNode(graph, "route");
        FlowNode branch1 = node(graph, new SimpleStep("branch1"));
        FlowNode branch2 = node(graph, new SimpleStep("branch2"));

        graph.addEdge(start, decider, new FlowEdge());
        graph.addEdge(decider, branch1, new FlowEdge("*"));
        graph.addEdge(decider, branch2, new FlowEdge("*"));
        graph.addEdge(branch1, end, new FlowEdge());
        graph.addEdge(branch2, end, new FlowEdge());

        assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, end).validate());
    }

    @Test
    void shouldDetectIllegalCycle() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode step1 = node(graph, new SimpleStep("step1"));
        FlowNode step2 = node(graph, new SimpleStep("step2"));

        graph.addEdge(start, step1, new FlowEdge());
        graph.addEdge(step1, step2, new FlowEdge());
        graph.addEdge(step2, step1, new FlowEdge());
        graph.addEdge(step2, end, new FlowEdge());

        FlowValidationException ex = assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, end).validate());
        assertTrue(ex.getMessage().contains("Illegal cycle"));
    }

    @Test
    void shouldAllowLoopBackCycle() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode loopBody = node(graph, new SimpleStep("loop-body"));
        FlowNode decider = deciderNode(graph, "loop-decider");

        graph.addEdge(start, loopBody, new FlowEdge());
        graph.addEdge(loopBody, decider, new FlowEdge());
        graph.addEdge(decider, loopBody, new FlowEdge("LOOP"));
        graph.addEdge(decider, end, new FlowEdge("*"));

        assertDoesNotThrow(() -> new FlowValidator(graph, start, end).validate());
    }

    @Test
    void shouldDetectParallelWithoutEnd() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode parallelStart = parallelStartNode(graph);
        FlowNode branch = node(graph, new SimpleStep("branch"));

        graph.addEdge(start, parallelStart, new FlowEdge());
        graph.addEdge(parallelStart, branch, new FlowEdge());
        graph.addEdge(branch, end, new FlowEdge());

        FlowValidationException ex = assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, end).validate());
        assertTrue(ex.getMessage().contains("PARALLEL_END"));
    }

    @Test
    void shouldDetectParallelWithNoBranches() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode parallelStart = parallelStartNode(graph);
        FlowNode parallelEnd = parallelEndNode(graph);

        graph.addEdge(start, parallelStart, new FlowEdge());
        graph.addEdge(parallelStart, parallelEnd, new FlowEdge());
        graph.addEdge(parallelEnd, end, new FlowEdge());

        FlowValidationException ex = assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, end).validate());
        assertTrue(ex.getMessage().contains("no branches"));
    }

    @Test
    void shouldPassValidParallelStructure() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);
        FlowNode end = node(graph, EndStep.INSTANCE);
        FlowNode parallelStart = parallelStartNode(graph);
        FlowNode parallelEnd = parallelEndNode(graph);
        FlowNode branchA = node(graph, new SimpleStep("branchA"));
        FlowNode branchB = node(graph, new SimpleStep("branchB"));

        graph.addEdge(start, parallelStart, new FlowEdge());
        graph.addEdge(parallelStart, branchA, new FlowEdge());
        graph.addEdge(parallelStart, branchB, new FlowEdge());
        graph.addEdge(branchA, parallelEnd, new FlowEdge());
        graph.addEdge(branchB, parallelEnd, new FlowEdge());
        graph.addEdge(parallelEnd, end, new FlowEdge());

        assertDoesNotThrow(() -> new FlowValidator(graph, start, end).validate());
    }

    @Test
    void shouldThrowWhenStartNodeNull() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode end = node(graph, EndStep.INSTANCE);

        assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, null, end).validate());
    }

    @Test
    void shouldThrowWhenEndNodeNull() {
        Graph<FlowNode, FlowEdge> graph = buildGraph();
        FlowNode start = node(graph, StartStep.INSTANCE);

        assertThrows(FlowValidationException.class,
                () -> new FlowValidator(graph, start, null).validate());
    }

    private Graph<FlowNode, FlowEdge> buildGraph() {
        return new DirectedPseudograph<>(FlowEdge.class);
    }

    private FlowNode node(Graph<FlowNode, FlowEdge> graph, io.github.aimi.rag.workflow.core.step.Step<?, ?> step) {
        FlowNode node = new FlowNode(step);
        graph.addVertex(node);
        return node;
    }

    private FlowNode deciderNode(Graph<FlowNode, FlowEdge> graph, String name) {
        FlowDecider decider = ctx -> "*";
        FlowNode node = new FlowNode(decider, name);
        graph.addVertex(node);
        return node;
    }

    private FlowNode parallelStartNode(Graph<FlowNode, FlowEdge> graph) {
        FlowNode node = new FlowNode(new ParallelStartStep(null));
        graph.addVertex(node);
        return node;
    }

    private FlowNode parallelEndNode(Graph<FlowNode, FlowEdge> graph) {
        FlowNode node = new FlowNode(new ParallelEndStep());
        graph.addVertex(node);
        return node;
    }
}
