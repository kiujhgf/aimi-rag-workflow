package io.github.aimi.rag.workflow.generation.memory;

import io.github.aimi.rag.workflow.generation.history.ChatCompressionUsageRecord;
import io.github.aimi.rag.workflow.generation.history.ChatCompressionUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link ChatMemory} that keeps the full conversation in a delegate
 * {@link ChatMemoryRepository} but, when the estimated token count exceeds a
 * threshold, compresses older messages (all but the most recent N) into an
 * LLM-generated summary before returning them.
 *
 * <p>When compression triggers, the older messages are replaced by the LLM
 * summary directly in the repository (lossy), so subsequent reads return the
 * already-compressed window without re-invoking the model. Compression failure
 * degrades to returning the uncompressed recent window, never propagating the
 * exception.</p>
 */
public class CompressingChatMemory implements ChatMemory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressingChatMemory.class);

    private static final String DEFAULT_SUMMARY_PROMPT =
            "Summarize the following conversation so far, preserving key facts, entities and decisions. "
                    + "Return only the summary text:\n\n";

    private final ChatMemoryRepository repository;
    private final ChatModel chatModel;
    private final TokenCountEstimator tokenCountEstimator;
    private final int maxTokens;
    private final int keepRecentMessages;
    private final String summaryPrompt;
    private final ChatCompressionUsageRepository usageRepository;

    private CompressingChatMemory(Builder builder) {
        this.repository = Objects.requireNonNull(builder.repository, "repository must not be null");
        this.chatModel = Objects.requireNonNull(builder.chatModel, "chatModel must not be null");
        this.tokenCountEstimator = Objects.requireNonNull(
                builder.tokenCountEstimator, "tokenCountEstimator must not be null");
        this.maxTokens = builder.maxTokens;
        this.keepRecentMessages = builder.keepRecentMessages;
        this.summaryPrompt = (builder.summaryPrompt == null || builder.summaryPrompt.isBlank())
                ? DEFAULT_SUMMARY_PROMPT
                : builder.summaryPrompt;
        this.usageRepository = builder.usageRepository;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = repository.findByConversationId(conversationId);
        if (all.isEmpty()) {
            return List.of();
        }
        if (tokenCountEstimator.estimate(all) <= maxTokens) {
            return all;
        }

        int split = Math.max(0, all.size() - keepRecentMessages);
        List<Message> recent = all.subList(split, all.size());
        List<Message> old = all.subList(0, split);
        if (old.isEmpty()) {
            return all;
        }

        try {
            List<Message> compressed = withSummary(summarize(conversationId, old), recent);
            repository.saveAll(conversationId, compressed);
            return compressed;
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to compress chat memory for conversation '{}'; degrading to window", conversationId, e);
            return recent;
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> all = new ArrayList<>(repository.findByConversationId(conversationId));
        all.addAll(messages);
        repository.saveAll(conversationId, all);
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    private String summarize(String conversationId, List<Message> oldMessages) {
        StringBuilder text = new StringBuilder();
        for (Message message : oldMessages) {
            String content = message.getText();
            if (content != null) {
                text.append(message.getMessageType()).append(": ").append(content).append('\n');
            }
        }

        StringBuilder summary = new StringBuilder();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        Instant start = Instant.now();

        try {
            chatModel.stream(new Prompt(new UserMessage(summaryPrompt + text)))
                    .doOnNext(chunk -> accumulate(chunk, summary, usageRef))
                    .blockLast();

            recordUsage(conversationId, oldMessages.size(), usageRef.get(), start, Instant.now(), null);
            String answer = summary.toString();
            return answer.isBlank() ? "" : answer;
        } catch (RuntimeException e) {
            recordUsage(conversationId, oldMessages.size(), usageRef.get(), start, Instant.now(), errorMessage(e));
            throw e;
        }
    }

    private void recordUsage(String conversationId, int compressedMessageCount, Usage usage,
                             Instant start, Instant end, String errorMessage) {
        if (usageRepository == null) {
            return;
        }
        String status = errorMessage == null
                ? ChatCompressionUsageRepository.STATUS_SUCCESS
                : ChatCompressionUsageRepository.STATUS_ERROR;
        usageRepository.saveUsage(new ChatCompressionUsageRecord(
                conversationId, status, errorMessage, compressedMessageCount,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                start, end));
    }

    private static void accumulate(ChatResponse chunk, StringBuilder summary, AtomicReference<Usage> usageRef) {
        if (chunk == null) {
            return;
        }
        if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
            String text = chunk.getResult().getOutput().getText();
            if (text != null) {
                summary.append(text);
            }
        }
        if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
            usageRef.set(chunk.getMetadata().getUsage());
        }
    }

    private static String errorMessage(Throwable e) {
        return e == null ? null : (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private static List<Message> withSummary(String summary, List<Message> recent) {
        List<Message> result = new ArrayList<>(recent.size() + 1);
        result.add(new SystemMessage(summary));
        result.addAll(recent);
        return result;
    }

    public static final class Builder {
        private ChatMemoryRepository repository;
        private ChatModel chatModel;
        private TokenCountEstimator tokenCountEstimator;
        private int maxTokens;
        private int keepRecentMessages;
        private String summaryPrompt = DEFAULT_SUMMARY_PROMPT;
        private ChatCompressionUsageRepository usageRepository;

        private Builder() {}

        public Builder repository(ChatMemoryRepository repository) {
            this.repository = repository;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder keepRecentMessages(int keepRecentMessages) {
            this.keepRecentMessages = keepRecentMessages;
            return this;
        }

        public Builder summaryPrompt(String summaryPrompt) {
            this.summaryPrompt = summaryPrompt;
            return this;
        }

        /** 压缩用量记录器（可选，用于把压缩 LLM 调用的 token 用量落库）。 */
        public Builder usageRepository(ChatCompressionUsageRepository usageRepository) {
            this.usageRepository = usageRepository;
            return this;
        }

        public CompressingChatMemory build() {
            return new CompressingChatMemory(this);
        }
    }
}
