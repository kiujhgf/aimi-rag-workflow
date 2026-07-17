package io.github.aimi.rag.workflow.core.exception;

public class StepValidationException extends RuntimeException {

    private final String stepName;

    public StepValidationException(String stepName, String message) {
        super("Step '" + stepName + "' validation failed: " + message);
        this.stepName = stepName;
    }

    public StepValidationException(String stepName, String message, Throwable cause) {
        super("Step '" + stepName + "' validation failed: " + message, cause);
        this.stepName = stepName;
    }

    public String getStepName() {
        return stepName;
    }
}