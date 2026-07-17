package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.flow.TestSteps;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractStepRetryTest {

    @Test
    void shouldSucceedWithoutRetry() {
        TestSteps.SimpleStep step = new TestSteps.SimpleStep("test-step");
        FlowContext context = FlowContext.create();

        step.process(context);

        assertEquals(1, step.getExecutionCount());
        assertEquals(StepStatus.FINISHED, step.getStatus());
    }

    @Test
    void shouldFailWithoutRetryByDefault() {
        TestSteps.AlwaysFailingStep step = new TestSteps.AlwaysFailingStep("failing-step");
        FlowContext context = FlowContext.create();

        assertThrows(StepExecuteException.class, () -> step.process(context));
        assertEquals(StepStatus.FAILED, step.getStatus());
    }

    @Test
    void shouldRetryAndSucceedOnRetryableException() {
        TestSteps.RetryableFailingStep step = new TestSteps.RetryableFailingStep("retry-step", 2);
        step.setRetryTimes(3);
        FlowContext context = FlowContext.create();

        step.process(context);

        assertEquals(3, step.getAttempts());
        assertEquals(StepStatus.FINISHED, step.getStatus());
    }

    @Test
    void shouldFailAfterExhaustingRetries() {
        TestSteps.RetryableFailingStep step = new TestSteps.RetryableFailingStep("retry-step", 5);
        step.setRetryTimes(2);
        FlowContext context = FlowContext.create();

        assertThrows(StepExecuteException.class, () -> step.process(context));
        assertEquals(2, step.getAttempts());
        assertEquals(StepStatus.FAILED, step.getStatus());
    }

    @Test
    void shouldReturnDefaultRetryTimes() {
        TestSteps.SimpleStep step = new TestSteps.SimpleStep("test-step");
        assertEquals(1, step.getRetryTimes());
    }

    @Test
    void shouldSetRetryTimes() {
        TestSteps.SimpleStep step = new TestSteps.SimpleStep("test-step");
        step.setRetryTimes(5);
        assertEquals(5, step.getRetryTimes());
    }

    @Test
    void shouldHaveRetryableExceptions() {
        TestSteps.SimpleStep step = new TestSteps.SimpleStep("test-step");
        assertNotNull(step.getRetryableExceptions());
        assertTrue(step.getRetryableExceptions().length > 0);
    }
}
