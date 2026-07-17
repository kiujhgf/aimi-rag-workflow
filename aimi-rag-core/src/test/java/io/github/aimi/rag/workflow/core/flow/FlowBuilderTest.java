package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowValidationException;
import io.github.aimi.rag.workflow.core.flow.TestSteps.SimpleStep;
import io.github.aimi.rag.workflow.core.model.FlowDecider;
import io.github.aimi.rag.workflow.core.step.EndStep;
import io.github.aimi.rag.workflow.core.step.ParallelEndStep;
import io.github.aimi.rag.workflow.core.step.ParallelStartStep;
import io.github.aimi.rag.workflow.core.step.StartStep;
import org.jgrapht.Graph;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FlowBuilderTest {

    @Test
    void shouldBuildEmptyFlowWithAutoStartEnd() {
        Flow flow = FlowBuilder.create().build();

        assertNotNull(flow);
        assertNotNull(flow.getStartNode());
        assertNotNull(flow.getEndNode());
        assertEquals(StartStep.INSTANCE, flow.getStartNode().getStep());
        assertEquals(EndStep.INSTANCE, flow.getEndNode().getStep());
    }

    @Test
    void shouldBuildSequentialFlow() {
        SimpleStep step1 = new SimpleStep("step1");
        SimpleStep step2 = new SimpleStep("step2");

        Flow flow = FlowBuilder.create()
                .start()
                .step(step1)
                .step(step2)
                .end()
                .build();

        Graph<FlowNode, FlowEdge> graph = flow.getGraph();
        Set<FlowNode> vertices = graph.vertexSet();

        assertTrue(vertices.size() >= 4);
        assertNotNull(findEdge(graph, flow.getStartNode(), new FlowNode(step1)));
        assertNotNull(findEdge(graph, new FlowNode(step1), new FlowNode(step2)));
        assertNotNull(findEdge(graph, new FlowNode(step2), flow.getEndNode()));
    }

    @Test
    void shouldBuildConditionalBranch() {
        SimpleStep stepA = new SimpleStep("branchA");
        SimpleStep stepB = new SimpleStep("branchB");
        FlowDecider decider = ctx -> "A";

        Flow flow = FlowBuilder.create()
                .start()
                .decider(decider, "route-decider")
                .on("A").to(stepA)
                .on("B").to(stepB)
                .build();

        Graph<FlowNode, FlowEdge> graph = flow.getGraph();
        FlowNode deciderNode = findDeciderNode(graph, "route-decider");

        assertNotNull(deciderNode);
        FlowEdge edgeA = findEdge(graph, deciderNode, new FlowNode(stepA));
        FlowEdge edgeB = findEdge(graph, deciderNode, new FlowNode(stepB));

        assertNotNull(edgeA);
        assertEquals("A", edgeA.getStatus());
        assertNotNull(edgeB);
        assertEquals("B", edgeB.getStatus());
    }

    @Test
    void shouldBuildConditionalWithWildcardBranch() {
        SimpleStep defaultStep = new SimpleStep("default");
        FlowDecider decider = ctx -> "unknown";

        Flow flow = FlowBuilder.create()
                .start()
                .decider(decider, "route")
                .on("A").to(new SimpleStep("branchA"))
                .on("*").to(defaultStep)
                .from(defaultStep)
                .build();

        Graph<FlowNode, FlowEdge> graph = flow.getGraph();
        FlowNode deciderNode = findDeciderNode(graph, "route");
        FlowNode defaultNode = new FlowNode(defaultStep);
        FlowEdge wildcardEdge = findEdge(graph, deciderNode, defaultNode);

        assertNotNull(wildcardEdge);
        assertTrue(wildcardEdge.isWildcard());
        assertNotNull(findEdge(graph, defaultNode, flow.getEndNode()));
    }

    @Test
    void shouldBuildLoop() {
        SimpleStep loopStep = new SimpleStep("loop-body");
        FlowDecider decider = ctx -> "continue";

        Flow flow = FlowBuilder.create()
                .start()
                .loop(decider)
                .step(loopStep)
                .endLoop()
                .end()
                .build();

        Graph<FlowNode, FlowEdge> graph = flow.getGraph();
        FlowNode loopStepNode = new FlowNode(loopStep);
        FlowNode deciderNode = findLoopDeciderNode(graph);

        assertNotNull(deciderNode);
        FlowEdge loopBackEdge = findEdge(graph, deciderNode, loopStepNode);
        assertNotNull(loopBackEdge);
        assertEquals("LOOP", loopBackEdge.getStatus());
    }

    @Test
    void shouldThrowWhenLoopNotClosed() {
        SimpleStep step = new SimpleStep("step");
        FlowDecider decider = ctx -> "continue";

        assertThrows(FlowValidationException.class, () ->
                FlowBuilder.create()
                        .start()
                        .loop(decider)
                        .step(step)
                        .build());
    }

    @Test
    void shouldThrowWhenLoopBodyEmpty() {
        FlowDecider decider = ctx -> "continue";

        assertThrows(FlowValidationException.class, () ->
                FlowBuilder.create()
                        .start()
                        .loop(decider)
                        .endLoop()
                        .build());
    }

    @Test
    void shouldThrowWhenNestedLoop() {
        FlowDecider decider1 = ctx -> "a";
        FlowDecider decider2 = ctx -> "b";

        assertThrows(FlowValidationException.class, () ->
                FlowBuilder.create()
                        .start()
                        .loop(decider1)
                        .loop(decider2)
                        .build());
    }

    @Test
    void shouldBuildParallel() {
        SimpleStep branchAStep = new SimpleStep("parallelA");
        SimpleStep branchBStep = new SimpleStep("parallelB");

        Flow flow = FlowBuilder.create()
                .start()
                .parallel()
                .on()
                .step(branchAStep)
                .on()
                .step(branchBStep)
                .endParallel()
                .end()
                .build();

        Graph<FlowNode, FlowEdge> graph = flow.getGraph();
        FlowNode parallelStart = findStepByName(graph, "PARALLEL_START");
        FlowNode parallelEnd = findStepByName(graph, "PARALLEL_END");

        assertNotNull(parallelStart);
        assertNotNull(parallelEnd);
        assertNotNull(findEdge(graph, parallelStart, new FlowNode(branchAStep)));
        assertNotNull(findEdge(graph, parallelStart, new FlowNode(branchBStep)));
        assertNotNull(findEdge(graph, new FlowNode(branchAStep), parallelEnd));
        assertNotNull(findEdge(graph, new FlowNode(branchBStep), parallelEnd));
    }

    @Test
    void shouldThrowWhenParallelNotClosed() {
        SimpleStep step = new SimpleStep("step");

        assertThrows(FlowValidationException.class, () ->
                FlowBuilder.create()
                        .start()
                        .parallel()
                        .on()
                        .step(step)
                        .build());
    }

    @Test
    void shouldThrowWhenNestedParallel() {
        assertThrows(FlowValidationException.class, () ->
                FlowBuilder.create()
                        .start()
                        .parallel()
                        .on()
                        .parallel()
                        .build());
    }

    @Test
    void shouldSupportFromStep() {
        SimpleStep step1 = new SimpleStep("step1");
        SimpleStep step2 = new SimpleStep("step2");
        SimpleStep step3 = new SimpleStep("step3");

        Flow flow = FlowBuilder.create()
                .start()
                .step(step1)
                .step(step2)
                .from(step1)
                .step(step3)
                .build();

        Graph<FlowNode, FlowEdge> graph = flow.getGraph();
        FlowNode step1Node = new FlowNode(step1);
        FlowNode step3Node = new FlowNode(step3);

        assertNotNull(findEdge(graph, step1Node, step3Node));
    }

    @Test
    void shouldSupportToFlowEndStep() {
        SimpleStep earlyEnd = new SimpleStep("early-end");
        FlowDecider decider = ctx -> "end";

        Flow flow = FlowBuilder.create()
                .start()
                .decider(decider, "check")
                .on("end").to(Flow.END_STEP)
                .on("*").to(new SimpleStep("continue"))
                .build();

        Graph<FlowNode, FlowEdge> graph = flow.getGraph();
        FlowNode deciderNode = findDeciderNode(graph, "check");
        FlowEdge endEdge = findEdge(graph, deciderNode, flow.getEndNode());

        assertNotNull(endEdge);
        assertEquals("end", endEdge.getStatus());
    }

    @Test
    void shouldUseCustomLoopConfig() {
        SimpleStep loopStep = new SimpleStep("loop-step");
        FlowDecider decider = ctx -> "continue";

        LoopConfig config = LoopConfig.max(5);

        Flow flow = FlowBuilder.create()
                .start()
                .loop(decider, config)
                .step(loopStep)
                .endLoop()
                .end()
                .build();

        assertNotNull(flow);
    }

    private FlowEdge findEdge(Graph<FlowNode, FlowEdge> graph, FlowNode source, FlowNode target) {
        Set<FlowEdge> edges = graph.getAllEdges(source, target);
        return edges == null || edges.isEmpty() ? null : edges.iterator().next();
    }

    private FlowNode findDeciderNode(Graph<FlowNode, FlowEdge> graph, String name) {
        for (FlowNode node : graph.vertexSet()) {
            if (node.isDecider() && name.equals(node.getName())) {
                return node;
            }
        }
        return null;
    }

    private FlowNode findLoopDeciderNode(Graph<FlowNode, FlowEdge> graph) {
        for (FlowNode node : graph.vertexSet()) {
            if (!node.isDecider()) {
                continue;
            }
            for (FlowEdge edge : graph.outgoingEdgesOf(node)) {
                if ("LOOP".equals(edge.getStatus())) {
                    return node;
                }
            }
        }
        return null;
    }

    private FlowNode findStepByName(Graph<FlowNode, FlowEdge> graph, String name) {
        for (FlowNode node : graph.vertexSet()) {
            if (node.isStep() && name.equals(node.getName())) {
                return node;
            }
        }
        return null;
    }
}
