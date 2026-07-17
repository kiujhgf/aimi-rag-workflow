package io.github.aimi.rag.workflow.core.exception;

/**
 * Thrown when any step in a flow fails, wrapping the original exception
 * with the failing step's name for diagnostics.
 */
public class FlowException extends RuntimeException {

    private final String stepName;

    public FlowException(String message) {
        super(message);
        this.stepName = null;
    }

    public FlowException(String stepName, Throwable cause) {
        super("Flow failed at step '" + stepName + "': " + cause.getMessage(), cause);
        this.stepName = stepName;
    }

    public String getStepName() {
        return stepName;
    }
}
