package io.github.aimi.rag.workflow.core.exception;

public class ResolveOutputException extends RuntimeException {

    private final String stepName;
    private final String outputKey;

    public ResolveOutputException(String stepName, String outputKey, String message) {
        super("Step '" + stepName + "' failed to resolve output '" + outputKey + "': " + message);
        this.stepName = stepName;
        this.outputKey = outputKey;
    }

    public ResolveOutputException(String stepName, String outputKey, Throwable cause) {
        super("Step '" + stepName + "' failed to resolve output '" + outputKey + "': " + cause.getMessage(), cause);
        this.stepName = stepName;
        this.outputKey = outputKey;
    }

    public String getStepName() {
        return stepName;
    }

    public String getOutputKey() {
        return outputKey;
    }
}