package io.github.aimi.rag.workflow.ingest.model;

import java.util.Map;

/**
 * Common contract for data that can be persisted by any storage backend
 * (Elasticsearch, Milvus, Pinecone, etc.).
 * Both {@link Chunk} and {@link Embedding} implement this.
 */
public interface StorageItem {

    String getContent();

    Map<String, Object> getMetadata();
}
