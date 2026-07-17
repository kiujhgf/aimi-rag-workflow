package io.github.aimi.rag.workflow.core.flow;

import java.time.Instant;

/**
 * Record of a step failure, persisted via FlowExecutionRepository.
 */
public class StepFailureRecord {

    private final String executionId;
    private final String stepName;
    private final String errorMessage;
    private final String errorStack;
    private final Instant timestamp;

    public StepFailureRecord(String executionId, String stepName,
                             String errorMessage, String errorStack, Instant timestamp) {
        this.executionId = executionId;
        this.stepName = stepName;
        this.errorMessage = errorMessage;
        this.errorStack = errorStack;
        this.timestamp = timestamp;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getStepName() {
        return stepName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorStack() {
        return errorStack;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String executionId;
        private String stepName;
        private String errorMessage;
        private String errorStack;
        private Instant timestamp;

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorStack(String errorStack) {
            this.errorStack = errorStack;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public StepFailureRecord build() {
            return new StepFailureRecord(executionId, stepName,
                    errorMessage, errorStack, timestamp);
        }
    }
}
