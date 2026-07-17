package io.github.aimi.rag.workflow.core.exception;

public class FlowValidationException extends RuntimeException {

    public FlowValidationException(String message) {
        super("Flow validation failed: " + message);
    }

    public FlowValidationException(String message, Throwable cause) {
        super("Flow validation failed: " + message, cause);
    }
}