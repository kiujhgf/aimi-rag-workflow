package io.github.aimi.rag.workflow.generation.history;

import java.time.Instant;

/**
 * A single persisted chat message row in {@code chat_message}.
 *
 * <p>One row represents one message (user/assistant/system). A single LLM turn
 * maps to two rows sharing the same {@code conversationId} and
 * {@code turnSequence}, distinguished by {@code messageIndex}.</p>
 */
public record ChatMessageRecord(
        String conversationId,
        int turnSequence,
        int messageIndex,
        String messageRole,
        String content,
        Instant createdTime) {
}
