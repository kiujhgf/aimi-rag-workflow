package io.github.aimi.rag.workflow.generation.history;

import java.time.Instant;

/**
 * A single persisted compression usage row in {@code chat_compression_usage}.
 *
 * <p>One row represents one LLM call used to compress working memory. Unlike
 * {@code chat_turn_usage}, it has no {@code turn_sequence} because compression
 * is an internal operation, not a user-facing conversation turn.</p>
 */
public record ChatCompressionUsageRecord(
        String conversationId,
        String status,
        String errorMessage,
        Integer compressedMessageCount,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Instant startTime,
        Instant endTime) {
}
