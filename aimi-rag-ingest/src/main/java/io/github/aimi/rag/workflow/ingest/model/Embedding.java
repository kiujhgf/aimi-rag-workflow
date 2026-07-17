package io.github.aimi.rag.workflow.ingest.model;

import java.util.Map;

/**
 * An embedding vector associated with a source Chunk.
 */
public class Embedding implements StorageItem {

    private final Chunk sourceChunk;
    private final float[] vector;

    public Embedding(Chunk sourceChunk, float[] vector) {
        this.sourceChunk = sourceChunk;
        this.vector = vector;
    }

    @Override
    public String getContent() {
        return sourceChunk.getContent();
    }

    @Override
    public Map<String, Object> getMetadata() {
        return sourceChunk.getMetadata();
    }

    public Chunk getSourceChunk() { return sourceChunk; }
    public float[] getVector() { return vector; }
}
