package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.model.StepType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StartStepTest {

    @Test
    void shouldBeSingleton() {
        assertSame(StartStep.INSTANCE, StartStep.INSTANCE);
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("START", StartStep.INSTANCE.getName());
    }

    @Test
    void shouldReturnStartType() {
        assertEquals(StepType.START, StartStep.INSTANCE.getType());
    }

    @Test
    void shouldProcessSuccessfully() {
        FlowContext context = FlowContext.create();
        StepStatus result = StartStep.INSTANCE.process(context);
        assertEquals(StepStatus.FINISHED, result);
        assertEquals(StepStatus.FINISHED, StartStep.INSTANCE.getStatus());
    }

    @Test
    void shouldReturnVoidInputType() {
        assertEquals(Void.class, StartStep.INSTANCE.getInputType());
    }
}
