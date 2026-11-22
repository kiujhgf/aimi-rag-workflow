package io.github.aimi.rag.workflow.core.model;

public enum StepType {
    INPUT,
    PARSING,
    CHUNKING,
    EMBEDDING,
    STORAGE,
    START,
    END,
    RETRIEVAL,
    GENERATION,
    CUSTOM
}