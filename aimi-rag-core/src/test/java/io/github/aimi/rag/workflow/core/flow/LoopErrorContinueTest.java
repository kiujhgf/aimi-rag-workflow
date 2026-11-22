package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.FlowException;
import io.github.aimi.rag.workflow.core.exception.FlowValidationException;
import io.github.aimi.rag.workflow.core.flow.TestSteps.AlwaysFailingStep;
import io.github.aimi.rag.workflow.core.flow.TestSteps.RetryableFailingStep;
import io.github.aimi.rag.workflow.core.flow.TestSteps.SimpleStep;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepErrorInfo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoopErrorContinueTest {

    @Test
    void shouldContinueToNextIterationOnFailure() {
        RetryableFailingStep risky = new RetryableFailingStep("risky", 1);
        SimpleStep next = new SimpleStep("next", "next", "ok");
        AtomicInteger deciderCalls = new AtomicInteger(0);

        Flow flow = FlowBuilder.create()
                .start()
                .loop(ctx -> deciderCalls.incrementAndGet() == 1 ? "LOOP" : "EXIT")
                .step(risky).onFailureToLoopStart()
                .step(next)
                .endLoop()
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(2, risky.getAttempts(), "First fails, second succeeds");
        assertEquals(1, next.getExecutionCount(), "next runs only in second iteration");
        assertEquals(2, deciderCalls.get(), "Decider called after each iteration");
        assertEquals("ok", context.<String>get("next"));
    }

    @Test
    void shouldRunHandlerThenContinue() {
        RetryableFailingStep risky = new RetryableFailingStep("risky", 1);
        SimpleStep handler = new SimpleStep("handler");
        SimpleStep next = new SimpleStep("next", "next", "ok");
        AtomicInteger deciderCalls = new AtomicInteger(0);

        Flow flow = FlowBuilder.create()
                .start()
                .loop(ctx -> deciderCalls.incrementAndGet() == 1 ? "LOOP" : "EXIT")
                .step(risky).onFailureToLoopStart(handler)
                .step(next)
                .endLoop()
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        assertEquals(2, risky.getAttempts());
        assertEquals(1, handler.getExecutionCount(), "Handler runs once on first failure");
        assertEquals(1, next.getExecutionCount());
        assertEquals(2, deciderCalls.get());

        StepErrorInfo info = handler.getExecutedContexts().get(0).get(StepErrorInfo.CONTEXT_KEY);
        assertNotNull(info);
        assertEquals("risky", info.getStepName());
    }

    @Test
    void shouldAbortWhenEveryIterationFails() {
        AlwaysFailingStep failing = new AlwaysFailingStep("failing");

        Flow flow = FlowBuilder.create()
                .start()
                .loop(ctx -> "LOOP", LoopConfig.max(3))
                .step(failing).onFailureToLoopStart()
                .endLoop()
                .end()
                .build();

        FlowException ex = assertThrows(FlowException.class,
                () -> flow.execute(FlowContext.create()));

        assertTrue(ex.getMessage().contains("max iterations"));
    }

    @Test
    void shouldRejectOnFailureToLoopStartOutsideLoop() {
        RetryableFailingStep step = new RetryableFailingStep("risky", 1);

        assertThrows(FlowValidationException.class, () -> FlowBuilder.create()
                .start()
                .step(step).onFailureToLoopStart());
    }

    @Test
    void shouldRejectOnFailureToLoopStartWithHandlerOutsideLoop() {
        RetryableFailingStep step = new RetryableFailingStep("risky", 1);
        SimpleStep handler = new SimpleStep("handler");

        assertThrows(FlowValidationException.class, () -> FlowBuilder.create()
                .start()
                .step(step).onFailureToLoopStart(handler));
    }
}
