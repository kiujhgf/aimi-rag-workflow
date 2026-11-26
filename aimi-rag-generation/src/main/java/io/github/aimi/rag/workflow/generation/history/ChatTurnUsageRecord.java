package io.github.aimi.rag.workflow.generation.history;

import java.time.Instant;

/**
 * A single persisted turn-level usage row in {@code chat_turn_usage}.
 *
 * <p>One row represents one LLM call. Token usage belongs here, not on
 * {@code chat_message}, so that {@code total_tokens} is unambiguous per turn.</p>
 */
public record ChatTurnUsageRecord(
        String conversationId,
        int turnSequence,
        String model,
        String executionId,
        String status,
        String errorMessage,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Instant startTime,
        Instant endTime) {
}
