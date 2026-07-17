package io.github.aimi.rag.workflow.core.exception;

public class TypeMismatchException extends RuntimeException {

    private final String key;
    private final Class<?> expectedType;
    private final Class<?> actualType;

    public TypeMismatchException(String key, Class<?> expectedType, Class<?> actualType) {
        super("Type mismatch for key '" + key + "': expected " + expectedType.getSimpleName() 
                + ", got " + actualType.getSimpleName());
        this.key = key;
        this.expectedType = expectedType;
        this.actualType = actualType;
    }

    public String getKey() {
        return key;
    }

    public Class<?> getExpectedType() {
        return expectedType;
    }

    public Class<?> getActualType() {
        return actualType;
    }
}