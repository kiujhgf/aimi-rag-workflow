package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.flow.TestSteps.SimpleStep;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowContextKeys;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionMetadataTest {

    @Test
    void shouldGenerateExecutionIdInExecute() {
        Flow flow = FlowBuilder.create()
                .start()
                .step(new SimpleStep("step1"))
                .end()
                .build();

        FlowContext context = flow.execute(FlowContext.create());

        String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
        assertNotNull(executionId);
        assertFalse(executionId.isEmpty());
    }

    @Test
    void shouldPreserveExistingExecutionId() {
        Flow flow = FlowBuilder.create()
                .start()
                .step(new SimpleStep("step1"))
                .end()
                .build();

        FlowContext context = FlowContext.create();
        context.set(FlowContextKeys.EXECUTION_ID, "custom-id");

        flow.execute(context);

        assertEquals("custom-id", context.get(FlowContextKeys.EXECUTION_ID, String.class));
    }

    @Test
    void shouldTrackCurrentStep() {
        CurrentStepCapturingStep step1 = new CurrentStepCapturingStep("step1");
        CurrentStepCapturingStep step2 = new CurrentStepCapturingStep("step2");

        Flow flow = FlowBuilder.create()
                .start()
                .step(step1)
                .step(step2)
                .end()
                .build();

        flow.execute(FlowContext.create());

        assertEquals("step1", step1.getCapturedCurrentStep());
        assertEquals("step2", step2.getCapturedCurrentStep());
    }

    /** A step that records the {@code __current_step} value observed while it runs. */
    static class CurrentStepCapturingStep extends AbstractStep<Void, Void> {

        private final String stepName;
        private String capturedCurrentStep;

        CurrentStepCapturingStep(String stepName) {
            super(ctx -> null);
            this.stepName = stepName;
        }

        @Override
        public String getName() {
            return stepName;
        }

        @Override
        public StepType getType() {
            return StepType.INPUT;
        }

        @Override
        public FlowCategory getCategory() {
            return FlowCategory.INGEST;
        }

        @Override
        public Class<Void> getInputType() {
            return Void.class;
        }

        @Override
        public Void resolveInput(FlowContext context) {
            return null;
        }

        @Override
        protected Void doProcess(Void input, FlowContext context) {
            capturedCurrentStep = context.get(FlowContextKeys.CURRENT_STEP, String.class);
            return null;
        }

        @Override
        protected void writeOutput(Void output, FlowContext context) {
        }

        String getCapturedCurrentStep() {
            return capturedCurrentStep;
        }
    }

}
