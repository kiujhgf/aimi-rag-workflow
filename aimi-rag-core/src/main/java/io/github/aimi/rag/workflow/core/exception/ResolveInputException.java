package io.github.aimi.rag.workflow.core.exception;

public class ResolveInputException extends RuntimeException {

    private final String stepName;
    private final String inputKey;

    public ResolveInputException(String stepName, String inputKey, String message) {
        super("Step '" + stepName + "' failed to resolve input '" + inputKey + "': " + message);
        this.stepName = stepName;
        this.inputKey = inputKey;
    }

    public ResolveInputException(String stepName, String inputKey, Throwable cause) {
        super("Step '" + stepName + "' failed to resolve input '" + inputKey + "': " + cause.getMessage(), cause);
        this.stepName = stepName;
        this.inputKey = inputKey;
    }

    public String getStepName() {
        return stepName;
    }

    public String getInputKey() {
        return inputKey;
    }
}