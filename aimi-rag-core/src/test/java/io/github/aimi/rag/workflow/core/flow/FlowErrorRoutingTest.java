package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowException;
import io.github.aimi.rag.workflow.core.exception.FlowValidationException;
import io.github.aimi.rag.workflow.core.flow.TestSteps.AlwaysFailingStep;
import io.github.aimi.rag.workflow.core.flow.TestSteps.SimpleStep;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepErrorInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowErrorRoutingTest {

    @Test
    void shouldRouteToErrorHandlerWhenStepFails() {
        AlwaysFailingStep failing = new AlwaysFailingStep("failing");
        SimpleStep handler = new SimpleStep("handler", "handled", "yes");
        SimpleStep after = new SimpleStep("after", "after", "yes");

        Flow flow = FlowBuilder.create()
                .start()
                .step(failing).onFailure(handler)
                .step(after)
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, handler.getExecutionCount());
        assertEquals(0, after.getExecutionCount());
        assertEquals("yes", context.<String>get("handled"));
    }

    @Test
    void shouldWriteStepErrorInfoIntoContextBeforeRouting() {
        AlwaysFailingStep failing = new AlwaysFailingStep("failing");
        SimpleStep handler = new SimpleStep("handler");

        Flow flow = FlowBuilder.create()
                .start()
                .step(failing).onFailure(handler)
                .step(new SimpleStep("after"))
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        StepErrorInfo errorInfo = context.get(StepErrorInfo.CONTEXT_KEY);
        assertNotNull(errorInfo);
        assertEquals("failing", errorInfo.getStepName());
        assertNotNull(errorInfo.getErrorMessage());
        assertTrue(errorInfo.getErrorMessage().contains("Always fails"));
        assertNotNull(errorInfo.getError());
        assertNotNull(errorInfo.getTimestamp());
    }

    @Test
    void shouldAbortWithFlowExceptionWhenNoErrorRoute() {
        AlwaysFailingStep failing = new AlwaysFailingStep("failing");

        Flow flow = FlowBuilder.create()
                .start()
                .step(failing)
                .end()
                .build();

        assertThrows(FlowException.class, () -> flow.execute(FlowContext.create()));
    }

    @Test
    void shouldRouteToEndOnFailureToEnd() {
        AlwaysFailingStep failing = new AlwaysFailingStep("failing");
        SimpleStep after = new SimpleStep("after", "after", "yes");

        Flow flow = FlowBuilder.create()
                .start()
                .step(failing).onFailureToEnd()
                .step(after)
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(0, after.getExecutionCount());
        assertNull(context.<String>get("after"));
    }

    @Test
    void shouldNotExecuteErrorPathWhenStepSucceeds() {
        SimpleStep ok = new SimpleStep("ok", "ok", "yes");
        SimpleStep handler = new SimpleStep("handler", "handled", "yes");

        Flow flow = FlowBuilder.create()
                .start()
                .step(ok).onFailure(handler)
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, ok.getExecutionCount());
        assertEquals(0, handler.getExecutionCount());
        assertEquals("yes", context.<String>get("ok"));
        assertNull(context.<String>get("handled"));
    }

    @Test
    void shouldAbortWhenErrorHandlerItselfFailsWithoutRoute() {
        AlwaysFailingStep failing = new AlwaysFailingStep("failing");
        AlwaysFailingStep failingHandler = new AlwaysFailingStep("failing-handler");
        SimpleStep after = new SimpleStep("after");

        Flow flow = FlowBuilder.create()
                .start()
                .step(failing).onFailure(failingHandler)
                .step(after)
                .end()
                .build();

        assertThrows(FlowException.class, () -> flow.execute(FlowContext.create()));
        assertEquals(0, after.getExecutionCount());
    }

    @Test
    void shouldContinueNormalChainAfterOnFailureDeclaration() {
        SimpleStep ok = new SimpleStep("ok");
        SimpleStep handler = new SimpleStep("handler");
        SimpleStep next = new SimpleStep("next");

        Flow flow = FlowBuilder.create()
                .start()
                .step(ok).onFailure(handler)
                .step(next)
                .end()
                .build();

        flow.execute(FlowContext.create());

        assertEquals(1, ok.getExecutionCount());
        assertEquals(1, next.getExecutionCount());
        assertEquals(0, handler.getExecutionCount());
    }

    @Test
    void shouldRouteInsideParallelBranch() {
        SimpleStep branchOk = new SimpleStep("branch-ok");
        AlwaysFailingStep failing = new AlwaysFailingStep("failing");
        SimpleStep branchHandler = new SimpleStep("branch-handler");

        Flow flow = FlowBuilder.create()
                .start()
                .parallel()
                .on()
                .step(branchOk)
                .on()
                .step(failing).onFailure(branchHandler)
                .endParallel()
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(1, branchOk.getExecutionCount());
        assertEquals(1, branchHandler.getExecutionCount());
        assertNotNull(context);
    }

    @Test
    void shouldRejectOnFailureAfterDecider() {
        SimpleStep handler = new SimpleStep("handler");

        assertThrows(FlowValidationException.class, () -> FlowBuilder.create()
                .start()
                .decider(ctx -> "A", "route")
                .onFailure(handler));
    }
}
