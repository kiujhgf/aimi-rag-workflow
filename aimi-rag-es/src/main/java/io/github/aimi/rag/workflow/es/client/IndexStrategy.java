package io.github.aimi.rag.workflow.es.client;

/**
 * Determines how index existence is handled before writing documents.
 */
public enum IndexStrategy {

    /**
     * Create the index if it does not exist, otherwise use it as-is.
     * Suitable for development and testing.
     */
    CREATE_IF_NOT_EXISTS,

    /**
     * The index must already exist; throw an exception otherwise.
     * Suitable for production where indexes are provisioned by an operator.
     */
    STRICT,

    /**
     * Delete the index (if it exists) and recreate it fresh.
     * Suitable for integration tests that need a clean slate each run.
     */
    DROP_AND_CREATE,

    /**
     * Append documents without checking or creating the index.
     * Suitable for high-throughput scenarios where the caller guarantees
     * the index exists.
     */
    APPEND_ONLY
}
