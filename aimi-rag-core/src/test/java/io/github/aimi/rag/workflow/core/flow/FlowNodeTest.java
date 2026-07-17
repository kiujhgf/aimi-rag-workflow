package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.flow.TestSteps.SimpleStep;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowDecider;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowNodeTest {

    @Test
    void shouldCreateStepNode() {
        SimpleStep step = new SimpleStep("step1");
        FlowNode node = new FlowNode(step);

        assertTrue(node.isStep());
        assertFalse(node.isDecider());
        assertEquals("step1", node.getName());
        assertSame(step, node.getStep());
    }

    @Test
    void shouldCreateDeciderNode() {
        FlowDecider decider = ctx -> "yes";
        FlowNode node = new FlowNode(decider, "my-decider");

        assertTrue(node.isDecider());
        assertFalse(node.isStep());
        assertEquals("my-decider", node.getName());
        assertSame(decider, node.getDecider());
    }

    @Test
    void stepNodesShouldBeEqualByStepIdentity() {
        SimpleStep step = new SimpleStep("step1");
        FlowNode node1 = new FlowNode(step);
        FlowNode node2 = new FlowNode(step);

        assertEquals(node1, node2);
        assertEquals(node1.hashCode(), node2.hashCode());
    }

    @Test
    void stepNodesShouldNotBeEqualForDifferentSteps() {
        SimpleStep step1 = new SimpleStep("step1");
        SimpleStep step2 = new SimpleStep("step2");
        FlowNode node1 = new FlowNode(step1);
        FlowNode node2 = new FlowNode(step2);

        assertNotEquals(node1, node2);
    }

    @Test
    void deciderNodesShouldBeEqualByName() {
        FlowDecider decider1 = ctx -> "a";
        FlowDecider decider2 = ctx -> "b";
        FlowNode node1 = new FlowNode(decider1, "same-name");
        FlowNode node2 = new FlowNode(decider2, "same-name");

        assertEquals(node1, node2);
        assertEquals(node1.hashCode(), node2.hashCode());
    }

    @Test
    void deciderNodesShouldNotBeEqualForDifferentNames() {
        FlowDecider decider = ctx -> "a";
        FlowNode node1 = new FlowNode(decider, "name1");
        FlowNode node2 = new FlowNode(decider, "name2");

        assertNotEquals(node1, node2);
    }

    @Test
    void shouldExecuteStepNode() {
        SimpleStep step = new SimpleStep("exec-step", "key", "value");
        FlowNode node = new FlowNode(step);
        FlowContext context = FlowContext.create();

        StepStatus status = node.execute(context);

        assertEquals(StepStatus.FINISHED, status);
        assertEquals(1, step.getExecutionCount());
        assertEquals("value", context.<String>get("key"));
    }

    @Test
    void shouldDecideViaDeciderNode() {
        FlowDecider decider = ctx -> {
            ctx.set("decider-called", true);
            return "branch-a";
        };
        FlowNode node = new FlowNode(decider, "test-decider");
        FlowContext context = FlowContext.create();

        String result = node.decide(context);

        assertEquals("branch-a", result);
        assertTrue(context.<Boolean>get("decider-called"));
    }

    @Test
    void shouldReturnWildcardWhenDeciderNodeHasNoDecider() {
        FlowNode node = new FlowNode((FlowDecider) null, "empty-decider");
        FlowContext context = FlowContext.create();

        String result = node.decide(context);

        assertEquals("*", result);
    }

    @Test
    void shouldReturnFinishedStatusForDeciderNode() {
        FlowDecider decider = ctx -> "yes";
        FlowNode node = new FlowNode(decider, "decider");

        assertEquals(StepStatus.FINISHED, node.getStatus());
    }

    @Test
    void shouldBeEqualSameInstance() {
        SimpleStep step = new SimpleStep("step1");
        FlowNode node = new FlowNode(step);

        assertEquals(node, node);
    }

    @Test
    void shouldNotBeEqualNull() {
        SimpleStep step = new SimpleStep("step1");
        FlowNode node = new FlowNode(step);

        assertNotEquals(node, null);
    }

    @Test
    void shouldNotBeEqualDifferentType() {
        SimpleStep step = new SimpleStep("step1");
        FlowNode node = new FlowNode(step);

        assertNotEquals(node, "not-a-node");
    }

    @Test
    void shouldHaveMeaningfulToString() {
        SimpleStep step = new SimpleStep("my-step");
        FlowNode node = new FlowNode(step);

        String str = node.toString();
        assertTrue(str.contains("my-step"));
    }
}
