package io.github.aimi.rag.workflow.core.exception;

public class StepExecuteException extends RuntimeException {

    private final String stepName;

    public StepExecuteException(String stepName, String message) {
        super("Step '" + stepName + "' failed: " + message);
        this.stepName = stepName;
    }

    public StepExecuteException(String stepName, Throwable cause) {
        super("Step '" + stepName + "' failed: " + cause.getMessage(), cause);
        this.stepName = stepName;
    }

    public StepExecuteException(String stepName, String message, Throwable cause) {
        super("Step '" + stepName + "' failed: " + message, cause);
        this.stepName = stepName;
    }

    public String getStepName() {
        return stepName;
    }
}