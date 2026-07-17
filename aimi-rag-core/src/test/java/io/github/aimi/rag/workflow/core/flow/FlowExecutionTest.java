package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowException;
import io.github.aimi.rag.workflow.core.flow.TestSteps.AlwaysFailingStep;
import io.github.aimi.rag.workflow.core.flow.TestSteps.EndStepAction;
import io.github.aimi.rag.workflow.core.flow.TestSteps.SimpleStep;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowDecider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowExecutionTest {

    @Test
    void shouldExecuteSequentialFlow() {
        SimpleStep step1 = new SimpleStep("step1", "key1", "value1");
        SimpleStep step2 = new SimpleStep("step2", "key2", "value2");

        Flow flow = FlowBuilder.create()
                .start()
                .step(step1)
                .step(step2)
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, step1.getExecutionCount());
        assertEquals(1, step2.getExecutionCount());
        assertEquals("value1", context.<String>get("key1"));
        assertEquals("value2", context.<String>get("key2"));
    }

    @Test
    void shouldExecuteConditionalBranchA() {
        SimpleStep branchA = new SimpleStep("branchA", "branch", "A");
        SimpleStep branchB = new SimpleStep("branchB", "branch", "B");
        FlowDecider decider = ctx -> "A";

        Flow flow = FlowBuilder.create()
                .start()
                .decider(decider, "route")
                .on("A").to(branchA)
                .on("B").to(branchB)
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, branchA.getExecutionCount());
        assertEquals(0, branchB.getExecutionCount());
        assertEquals("A", context.<String>get("branch"));
    }

    @Test
    void shouldExecuteConditionalBranchB() {
        SimpleStep branchA = new SimpleStep("branchA", "branch", "A");
        SimpleStep branchB = new SimpleStep("branchB", "branch", "B");
        FlowDecider decider = ctx -> "B";

        Flow flow = FlowBuilder.create()
                .start()
                .decider(decider, "route")
                .on("A").to(branchA)
                .on("B").to(branchB)
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(0, branchA.getExecutionCount());
        assertEquals(1, branchB.getExecutionCount());
        assertEquals("B", context.<String>get("branch"));
    }

    @Test
    void shouldUseWildcardWhenNoMatch() {
        SimpleStep matched = new SimpleStep("matched", "hit", "yes");
        SimpleStep fallback = new SimpleStep("fallback", "hit", "no");
        FlowDecider decider = ctx -> "UNKNOWN";

        Flow flow = FlowBuilder.create()
                .start()
                .decider(decider, "route")
                .on("A").to(matched)
                .on("*").to(fallback)
                .from(fallback)
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(0, matched.getExecutionCount());
        assertEquals(1, fallback.getExecutionCount());
        assertEquals("no", context.<String>get("hit"));
    }

    @Test
    void shouldExecuteLoopUntilConditionMet() {
        SimpleStep loopBody = new SimpleStep("loop-body");
        FlowDecider decider = ctx -> {
            long count = ctx.incrementCounter("loop-count");
            if (count < 3) {
                return "LOOP";
            }
            return "EXIT";
        };

        Flow flow = FlowBuilder.create()
                .start()
                .loop(decider)
                .step(loopBody)
                .endLoop()
                .end()
                .build();

        flow.execute(FlowContext.create());

        assertEquals(3, loopBody.getExecutionCount());
    }

    @Test
    void shouldThrowWhenLoopExceedsMaxIterations() {
        SimpleStep loopBody = new SimpleStep("loop-body");
        FlowDecider decider = ctx -> "LOOP";

        Flow flow = FlowBuilder.create()
                .start()
                .loop(decider)
                .step(loopBody)
                .endLoop()
                .end()
                .build();

        FlowException ex = assertThrows(FlowException.class,
                () -> flow.execute(FlowContext.create()));

        assertTrue(ex.getMessage().contains("max iterations"));
        assertTrue(loopBody.getExecutionCount() > 0);
    }

    @Test
    void shouldExecuteParallelBranches() {
        SimpleStep branchA = new SimpleStep("branchA", "aKey", "aValue");
        SimpleStep branchB = new SimpleStep("branchB", "bKey", "bValue");

        Flow flow = FlowBuilder.create()
                .start()
                .parallel()
                .on()
                .step(branchA)
                .on()
                .step(branchB)
                .endParallel()
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, branchA.getExecutionCount());
        assertEquals(1, branchB.getExecutionCount());
        assertEquals("aValue", context.<String>get("aKey"));
        assertEquals("bValue", context.<String>get("bKey"));
    }

    @Test
    void shouldExecuteParallelWithMultipleStepsPerBranch() {
        SimpleStep a1 = new SimpleStep("a1", "a1key", "a1val");
        SimpleStep a2 = new SimpleStep("a2", "a2key", "a2val");
        SimpleStep b1 = new SimpleStep("b1", "b1key", "b1val");

        Flow flow = FlowBuilder.create()
                .start()
                .parallel()
                .on()
                .step(a1)
                .step(a2)
                .on()
                .step(b1)
                .endParallel()
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, a1.getExecutionCount());
        assertEquals(1, a2.getExecutionCount());
        assertEquals(1, b1.getExecutionCount());
        assertEquals("a1val", context.<String>get("a1key"));
        assertEquals("a2val", context.<String>get("a2key"));
        assertEquals("b1val", context.<String>get("b1key"));
    }

    @Test
    void shouldExecuteFlowWithEndStatusEarlyTermination() {
        SimpleStep before = new SimpleStep("before", "before", "yes");
        EndStepAction endAction = new EndStepAction("end-action");
        SimpleStep after = new SimpleStep("after", "after", "yes");

        Flow flow = FlowBuilder.create()
                .start()
                .step(before)
                .step(endAction)
                .step(after)
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, before.getExecutionCount());
        assertEquals(0, after.getExecutionCount());
        assertEquals("yes", context.<String>get("before"));
    }

    @Test
    void shouldThrowOnStepFailure() {
        AlwaysFailingStep failingStep = new AlwaysFailingStep("failing");

        Flow flow = FlowBuilder.create()
                .start()
                .step(failingStep)
                .end()
                .build();

        assertThrows(FlowException.class, () -> flow.execute(FlowContext.create()));
    }

    @Test
    void shouldExecuteEmptyFlow() {
        Flow flow = FlowBuilder.create().build();
        FlowContext context = flow.execute(FlowContext.create());
        assertNotNull(context);
    }

    @Test
    void shouldCreateContextWhenNull() {
        Flow flow = FlowBuilder.create().build();
        FlowContext context = flow.execute(null);
        assertNotNull(context);
    }

    @Test
    void shouldNotifyListeners() {
        SimpleStep step = new SimpleStep("step1");
        java.util.List<String> events = new java.util.ArrayList<>();

        FlowExecutionListener listener = new FlowExecutionListener() {
            @Override
            public void onFlowStart(FlowContext context) {
                events.add("flow-start");
            }

            @Override
            public void onStepStart(String stepName, FlowContext context) {
                events.add("step-start:" + stepName);
            }

            @Override
            public void onStepSuccess(String stepName, io.github.aimi.rag.workflow.core.model.StepStatus status, FlowContext context) {
                events.add("step-success:" + stepName);
            }

            @Override
            public void onFlowComplete(FlowContext context) {
                events.add("flow-complete");
            }
        };

        Flow flow = FlowBuilder.create()
                .start()
                .step(step)
                .end()
                .build();
        flow.addListener(listener);

        flow.execute(FlowContext.create());

        assertTrue(events.contains("flow-start"));
        assertTrue(events.contains("step-start:step1"));
        assertTrue(events.contains("step-success:step1"));
        assertTrue(events.contains("flow-complete"));
    }
}
