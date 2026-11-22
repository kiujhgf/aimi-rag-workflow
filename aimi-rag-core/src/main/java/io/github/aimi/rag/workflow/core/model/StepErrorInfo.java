package io.github.aimi.rag.workflow.core.model;

import java.time.Instant;

/**
 * Error details of a failed step, written into FlowContext under
 * {@link #CONTEXT_KEY} before routing to an error-handling node,
 * so error handlers can resolve it as their input.
 */
public class StepErrorInfo {

    public static final String CONTEXT_KEY = "flow.step.error";

    private final String stepName;
    private final String errorMessage;
    private final Throwable error;
    private final Instant timestamp;

    public StepErrorInfo(String stepName, Throwable error) {
        this.stepName = stepName;
        this.error = error;
        this.errorMessage = error != null ? error.getMessage() : null;
        this.timestamp = Instant.now();
    }

    public String getStepName() {
        return stepName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getError() {
        return error;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
