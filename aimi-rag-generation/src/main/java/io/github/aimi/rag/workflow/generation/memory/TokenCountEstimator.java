package io.github.aimi.rag.workflow.generation.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Estimates the token count of a list of chat messages.
 */
@FunctionalInterface
public interface TokenCountEstimator {

    int estimate(List<Message> messages);

    /**
     * A simple character-based estimator (one token per {@code charsPerToken} characters).
     */
    static TokenCountEstimator charsPerToken(int charsPerToken) {
        int divisor = Math.max(1, charsPerToken);
        return messages -> {
            long chars = messages == null ? 0 : messages.stream()
                    .map(Message::getText)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(String::length)
                    .sum();
            return (int) Math.ceil(chars / (double) divisor);
        };
    }

    static TokenCountEstimator defaultEstimator() {
        return charsPerToken(4);
    }
}
