package io.github.aimi.rag.workflow.ingest.model;

/**
 * Result returned by an IngestFlow execution.
 */
public class IngestResult {

    private final int storedCount;
    private final boolean success;

    public IngestResult(int storedCount, boolean success) {
        this.storedCount = storedCount;
        this.success = success;
    }

    public int getStoredCount() { return storedCount; }
    public boolean isSuccess() { return success; }
}
