package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.model.StepStatus;

import java.time.Instant;

/**
 * Record of a single step execution, persisted via FlowExecutionRepository.
 */
public class StepExecutionRecord {

    private final String executionId;
    private final String stepName;
    private final StepStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final Object input;
    private final Object output;

    public StepExecutionRecord(String executionId, String stepName, StepStatus status,
                               Instant startTime, Instant endTime,
                               Object input, Object output) {
        this.executionId = executionId;
        this.stepName = stepName;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.input = input;
        this.output = output;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getStepName() {
        return stepName;
    }

    public StepStatus getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Object getInput() {
        return input;
    }

    public Object getOutput() {
        return output;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String executionId;
        private String stepName;
        private StepStatus status;
        private Instant startTime;
        private Instant endTime;
        private Object input;
        private Object output;

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }

        public Builder status(StepStatus status) {
            this.status = status;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder input(Object input) {
            this.input = input;
            return this;
        }

        public Builder output(Object output) {
            this.output = output;
            return this;
        }

        public StepExecutionRecord build() {
            return new StepExecutionRecord(executionId, stepName, status,
                    startTime, endTime, input, output);
        }
    }
}
