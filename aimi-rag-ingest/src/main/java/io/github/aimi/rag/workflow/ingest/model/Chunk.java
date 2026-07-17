package io.github.aimi.rag.workflow.ingest.model;

import java.util.Collections;
import java.util.Map;

/**
 * A text chunk with optional metadata, the core unit of data flowing through the ingest pipeline.
 */
public class Chunk implements StorageItem {

    private final String content;
    private final Map<String, Object> metadata;

    public Chunk(String content, Map<String, Object> metadata) {
        this.content = content;
        this.metadata = Collections.unmodifiableMap(metadata);
    }

    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
}
