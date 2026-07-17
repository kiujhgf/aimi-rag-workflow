package io.github.aimi.rag.workflow.ingest.model;

/**
 * Result returned by a StorageStep after persisting data.
 */
public class StorageResult {

    private final int storedCount;

    public StorageResult(int storedCount) {
        this.storedCount = storedCount;
    }

    public int getStoredCount() { return storedCount; }
}
