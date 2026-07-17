package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.model.StepType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndStepTest {

    @Test
    void shouldBeSingleton() {
        assertSame(EndStep.INSTANCE, EndStep.INSTANCE);
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("END", EndStep.INSTANCE.getName());
    }

    @Test
    void shouldReturnEndType() {
        assertEquals(StepType.END, EndStep.INSTANCE.getType());
    }

    @Test
    void shouldProcessSuccessfully() {
        FlowContext context = FlowContext.create();
        StepStatus result = EndStep.INSTANCE.process(context);
        assertEquals(StepStatus.FINISHED, result);
        assertEquals(StepStatus.FINISHED, EndStep.INSTANCE.getStatus());
    }
}
