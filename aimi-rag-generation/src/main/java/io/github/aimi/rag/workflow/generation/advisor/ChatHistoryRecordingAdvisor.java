package io.github.aimi.rag.workflow.generation.advisor;

import io.github.aimi.rag.workflow.generation.history.ChatMessageRepository;
import io.github.aimi.rag.workflow.generation.history.ChatTurnUsageRecord;
import io.github.aimi.rag.workflow.generation.history.ChatTurnUsageRepository;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records full chat history and per-turn token usage into {@code chat_message}
 * and {@code chat_turn_usage}, independent of the working-memory window.
 *
 * <p>Mounted as the outermost advisor (its {@link #getOrder()} is lower than the
 * {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor}),
 * so that {@code before} sees the raw this-turn request before memory is
 * prepended. conversationId / executionId / model are passed via the advisor
 * context map, not ThreadLocal.</p>
 */
public class ChatHistoryRecordingAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String CONTEXT_KEY_EXECUTION_ID = "chat_history_execution_id";
    public static final String CONTEXT_KEY_MODEL = "chat_history_model";

    private static final String DEFAULT_NAME = "chat-history-recording";

    private final ChatMessageRepository messageRepository;
    private final ChatTurnUsageRepository usageRepository;
    private final int order;
    private final String name;

    private ChatHistoryRecordingAdvisor(Builder builder) {
        this.messageRepository = Objects.requireNonNull(builder.messageRepository, "messageRepository must not be null");
        this.usageRepository = Objects.requireNonNull(builder.usageRepository, "usageRepository must not be null");
        this.order = builder.order;
        this.name = builder.name == null ? DEFAULT_NAME : builder.name;
    }

    public static Builder builder(ChatMessageRepository messageRepository,
                                  ChatTurnUsageRepository usageRepository) {
        return new Builder(messageRepository, usageRepository);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        TurnContext context = TurnContext.from(request);
        int turnSequence = usageRepository.nextTurnSequence(context.conversationId());
        messageRepository.saveMessages(context.conversationId(), turnSequence, context.userContent(), null);
        Instant start = Instant.now();

        ChatClientResponse response;
        try {
            response = chain.nextCall(request);
        } catch (RuntimeException e) {
            usageRepository.saveUsage(usageRecord(context, turnSequence,
                    ChatTurnUsageRepository.STATUS_ERROR, errorMessage(e), null, start, Instant.now()));
            throw e;
        }

        String assistantContent = assistantContent(response);
        messageRepository.saveMessages(context.conversationId(), turnSequence, context.userContent(), assistantContent);
        usageRepository.saveUsage(usageRecord(context, turnSequence,
                ChatTurnUsageRepository.STATUS_SUCCESS, null, usageOf(response), start, Instant.now()));
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        TurnContext context = TurnContext.from(request);

        return Flux.defer(() -> {
            int turnSequence = usageRepository.nextTurnSequence(context.conversationId());
            messageRepository.saveMessages(context.conversationId(), turnSequence, context.userContent(), null);
            Instant start = Instant.now();

            StringBuilder content = new StringBuilder();
            AtomicReference<Usage> usageRef = new AtomicReference<>();

            return chain.nextStream(request)
                    .doOnNext(chunk -> accumulate(chunk, content, usageRef))
                    .doOnComplete(() -> {
                        Usage usage = usageRef.get();
                        messageRepository.saveMessages(context.conversationId(), turnSequence,
                                context.userContent(), content.toString());
                        usageRepository.saveUsage(usageRecord(context, turnSequence,
                                ChatTurnUsageRepository.STATUS_SUCCESS, null, usage, start, Instant.now()));
                    })
                    .doOnError(e -> usageRepository.saveUsage(usageRecord(context, turnSequence,
                            ChatTurnUsageRepository.STATUS_ERROR, errorMessage(e), null, start, Instant.now())))
                    .doOnCancel(() -> usageRepository.saveUsage(usageRecord(context, turnSequence,
                            ChatTurnUsageRepository.STATUS_CANCELLED, null, null, start, Instant.now())));
        });
    }

    private static void accumulate(ChatClientResponse chunk, StringBuilder content,
                                   AtomicReference<Usage> usageRef) {
        ChatResponse chatResponse = chunk == null ? null : chunk.chatResponse();
        if (chatResponse == null) {
            return;
        }
        if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
            String text = chatResponse.getResult().getOutput().getText();
            if (text != null) {
                content.append(text);
            }
        }
        if (chatResponse.getMetadata() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                usageRef.set(usage);
            }
        }
    }

    private static ChatTurnUsageRecord usageRecord(TurnContext context, int turnSequence, String status,
                                                   String errorMessage, Usage usage,
                                                   Instant start, Instant end) {
        return new ChatTurnUsageRecord(
                context.conversationId(), turnSequence, context.model(), context.executionId(),
                status, errorMessage,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                start, end);
    }

    private static String assistantContent(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            return null;
        }
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }

    private static Usage usageOf(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getMetadata() == null) {
            return null;
        }
        return response.chatResponse().getMetadata().getUsage();
    }

    private static String errorMessage(Throwable e) {
        return e == null ? null : (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    public static final class Builder {
        private final ChatMessageRepository messageRepository;
        private final ChatTurnUsageRepository usageRepository;
        private int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;
        private String name = DEFAULT_NAME;

        private Builder(ChatMessageRepository messageRepository, ChatTurnUsageRepository usageRepository) {
            this.messageRepository = messageRepository;
            this.usageRepository = usageRepository;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public ChatHistoryRecordingAdvisor build() {
            return new ChatHistoryRecordingAdvisor(this);
        }
    }

    private record TurnContext(String conversationId, String executionId, String model, String userContent) {

        static TurnContext from(ChatClientRequest request) {
            var context = request.context();
            String conversationId = value(context.get(ChatMemory.CONVERSATION_ID), ChatMemory.DEFAULT_CONVERSATION_ID);
            String executionId = value(context.get(CONTEXT_KEY_EXECUTION_ID), null);
            String model = value(context.get(CONTEXT_KEY_MODEL), null);
            String userContent = extractUserContent(request);
            return new TurnContext(conversationId, executionId, model, userContent);
        }

        private static String value(Object value, String fallback) {
            if (value == null) {
                return fallback;
            }
            String text = value.toString();
            return text.isBlank() ? fallback : text;
        }

        private static String extractUserContent(ChatClientRequest request) {
            if (request.prompt() == null || request.prompt().getInstructions() == null) {
                return null;
            }
            return request.prompt().getInstructions().stream()
                    .filter(m -> m.getMessageType() == MessageType.USER)
                    .map(Message::getText)
                    .filter(Objects::nonNull)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(null);
        }
    }
}
