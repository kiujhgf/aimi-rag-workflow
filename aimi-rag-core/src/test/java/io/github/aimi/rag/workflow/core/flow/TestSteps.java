package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.step.AbstractStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Test step utilities shared across flow tests.
 */
public final class TestSteps {

    private TestSteps() {
    }

    /**
     * A simple step that records its execution and optionally writes a value to context.
     */
    public static class SimpleStep extends AbstractStep<Void, Void> {

        private final String stepName;
        private final String contextKey;
        private final String contextValue;
        private final List<FlowContext> executedContexts = new ArrayList<>();
        private int executionCount = 0;

        public SimpleStep(String stepName) {
            this(stepName, null, null);
        }

        public SimpleStep(String stepName, String contextKey, String contextValue) {
            super(ctx -> null);
            this.stepName = stepName;
            this.contextKey = contextKey;
            this.contextValue = contextValue;
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
            executionCount++;
            executedContexts.add(context);
            if (contextKey != null && contextValue != null) {
                context.set(contextKey, contextValue);
            }
            return null;
        }

        @Override
        protected void writeOutput(Void output, FlowContext context) {
        }

        public int getExecutionCount() {
            return executionCount;
        }

        public List<FlowContext> getExecutedContexts() {
            return executedContexts;
        }
    }

    /**
     * A step that fails on first N attempts, then succeeds (for retry testing).
     */
    public static class RetryableFailingStep extends AbstractStep<Void, Void> {

        private final String stepName;
        private final int failTimes;
        private int attempts = 0;

        public RetryableFailingStep(String stepName, int failTimes) {
            super(ctx -> null);
            this.stepName = stepName;
            this.failTimes = failTimes;
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
            attempts++;
            if (attempts <= failTimes) {
                throw new RuntimeException("Simulated failure attempt " + attempts);
            }
            return null;
        }

        @Override
        protected void writeOutput(Void output, FlowContext context) {
        }

        public int getAttempts() {
            return attempts;
        }
    }

    /**
     * A step that always fails.
     */
    public static class AlwaysFailingStep extends AbstractStep<Void, Void> {

        private final String stepName;

        public AlwaysFailingStep(String stepName) {
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
            throw new RuntimeException("Always fails");
        }

        @Override
        protected void writeOutput(Void output, FlowContext context) {
        }
    }

    /**
     * A step that sets status to END (terminate flow early).
     */
    public static class EndStepAction extends AbstractStep<Void, Void> {

        private final String stepName;

        public EndStepAction(String stepName) {
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
            return null;
        }

        @Override
        protected void writeOutput(Void output, FlowContext context) {
        }

        @Override
        public StepStatus process(FlowContext context) {
            super.process(context);
            this.status = StepStatus.END;
            return StepStatus.END;
        }
    }
}
