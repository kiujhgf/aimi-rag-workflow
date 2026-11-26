package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowContextKeys;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.generation.advisor.ChatHistoryRecordingAdvisor;
import io.github.aimi.rag.workflow.generation.memory.ChatMemoryMode;
import io.github.aimi.rag.workflow.generation.memory.CompressingChatMemory;
import io.github.aimi.rag.workflow.generation.memory.TokenCountEstimator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Provider-agnostic chat generation step.
 *
 * <p>输入语义为「会话 id」：{@link #getInputType()} 为 {@code String}，默认从上下文
 * {@code conversation_id} 读取（可为空，空则用构建期 UUID 兜底）。本轮消息由
 * {@code systemPrompt}/{@code userPrompt} 组装（支持 {@code {key}} 占位符从
 * {@link FlowContext} 取值）。</p>
 *
 * <p>记忆/历史策略通过 {@link Builder#memoryMode(ChatMemoryMode)} 选择，屏蔽底层
 * advisor 细节：{@link ChatMemoryMode#WINDOW}（仅窗口工作记忆）、
 * {@link ChatMemoryMode#COMPRESSING}（LLM 上下文压缩）、
 * {@link ChatMemoryMode#ALL}（窗口记忆 + 全量历史留痕）。</p>
 *
 * <p>流式输出：通过 builder 配置 {@link Builder#streaming(boolean)} 后，本 Step 不再调用模型，
 * 而是把 {@code stream().content()} 返回的冷 {@code Flux<String>} 写入上下文 key
 * {@link #STREAM_KEY}，由下游节点自行订阅处理；该模式下不写默认的 {@code answer}。</p>
 *
 * <p>需要结构化（DTO / JSON schema）输出时，使用 {@link StructuredChatGenerationStep}。</p>
 */
public class ChatGenerationStep extends AbstractStep<String, String> {

    private static final String STEP_NAME = "chat-generation";

    /** 默认从上下文读取 conversationId 的 key。 */
    public static final String DEFAULT_CONVERSATION_ID_KEY = "conversation_id";

    /** 流式模式下写入上下文的 key。 */
    public static final String STREAM_KEY = "stream";

    private static final int DEFAULT_MAX_MESSAGES = 20;
    private static final int DEFAULT_MAX_TOKENS = 8000;
    private static final int DEFAULT_KEEP_RECENT_MESSAGES = 10;

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final String userPrompt;
    private final boolean streaming;
    private final ChatMemory chatMemory;
    private final String defaultConversationId;
    private final Advisor historyAdvisor;
    private final String model;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    private ChatGenerationStep(ChatClient chatClient,
                               String systemPrompt,
                               String userPrompt,
                               boolean streaming,
                               ChatMemory chatMemory,
                               Advisor historyAdvisor,
                               String model,
                               InputResolver<String> inputResolver,
                               OutputResolver<String> outputResolver) {
        super(inputResolver, outputResolver);
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.streaming = streaming;
        this.chatMemory = chatMemory;
        this.defaultConversationId = UUID.randomUUID().toString();
        this.historyAdvisor = historyAdvisor;
        this.model = model;
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    public static Builder builder(ChatClient chatClient) {
        return new Builder(chatClient);
    }

    public static ConfigurableBuilder builder() {
        return new ConfigurableBuilder();
    }

    @Override
    public String getName() {
        return STEP_NAME;
    }

    @Override
    public StepType getType() {
        return StepType.GENERATION;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.QUERY;
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @Override
    protected String doProcess(String conversationId, FlowContext context) throws StepExecuteException {
        String resolvedConversationId = (conversationId == null || conversationId.isBlank())
                ? defaultConversationId
                : conversationId;
        String system = ChatMessageAssembler.applyPlaceholders(systemPrompt, context);
        String user = ChatMessageAssembler.applyPlaceholders(userPrompt, context);
        if ((system == null || system.isBlank()) && (user == null || user.isBlank())) {
            throw new StepExecuteException(STEP_NAME,
                    "No messages: neither systemPrompt nor userPrompt is configured");
        }

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .advisors(a -> configureAdvisors(a, resolvedConversationId, context));
        if (system != null && !system.isBlank()) {
            spec = spec.system(system);
        }
        if (user != null && !user.isBlank()) {
            spec = spec.user(user);
        }

        if (!streaming) {
            return spec.call().content();
        }

        context.set(STREAM_KEY, spec.stream().content());
        return null;
    }

    private void configureAdvisors(ChatClient.AdvisorSpec spec, String conversationId, FlowContext context) {
        spec.param(ChatMemory.CONVERSATION_ID, conversationId);
        String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
        if (executionId != null) {
            spec.param(ChatHistoryRecordingAdvisor.CONTEXT_KEY_EXECUTION_ID, executionId);
        }
        if (model != null) {
            spec.param(ChatHistoryRecordingAdvisor.CONTEXT_KEY_MODEL, model);
        }

        List<Advisor> advisors = new ArrayList<>();
        if (historyAdvisor != null) {
            advisors.add(historyAdvisor);
        }
        advisors.add(memoryAdvisor);
        spec.advisors(advisors);
    }

    @Override
    protected void writeOutput(String output, FlowContext context) {
        if (!streaming) {
            super.writeOutput(output, context);
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    private static ChatMemory buildChatMemory(ChatMemoryMode mode, int maxMessages,
                                              ChatModel summaryChatModel, int maxTokens,
                                              int keepRecentMessages, String summaryPrompt) {
        return switch (mode) {
            case WINDOW, ALL -> MessageWindowChatMemory.builder()
                    .chatMemoryRepository(new InMemoryChatMemoryRepository())
                    .maxMessages(maxMessages)
                    .build();
            case COMPRESSING -> {
                if (summaryChatModel == null) {
                    throw new IllegalArgumentException("COMPRESSING 模式需要 summaryChatModel(...)");
                }
                yield CompressingChatMemory.builder()
                        .repository(new InMemoryChatMemoryRepository())
                        .chatModel(summaryChatModel)
                        .tokenCountEstimator(TokenCountEstimator.defaultEstimator())
                        .maxTokens(maxTokens)
                        .keepRecentMessages(keepRecentMessages)
                        .summaryPrompt(summaryPrompt)
                        .build();
            }
        };
    }

    public static class Builder {
        private final ChatClient chatClient;
        private String systemPrompt;
        private String userPrompt;
        private boolean streaming;
        private ChatMemoryMode memoryMode = ChatMemoryMode.WINDOW;
        private int maxMessages = DEFAULT_MAX_MESSAGES;
        private ChatModel summaryChatModel;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private int keepRecentMessages = DEFAULT_KEEP_RECENT_MESSAGES;
        private String summaryPrompt;
        private Advisor historyAdvisor;
        private InputResolver<String> inputResolver = defaultInputResolver();
        private OutputResolver<String> outputResolver = new DefaultOutputResolver<>("answer");

        private Builder(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        /**
         * 开启后把 {@code Flux<String>} 写入上下文 {@link #STREAM_KEY}，由下游节点订阅处理。
         */
        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder memoryMode(ChatMemoryMode memoryMode) {
            this.memoryMode = memoryMode;
            return this;
        }

        /** 窗口记忆的最大消息数（{@code WINDOW} / {@code ALL} 模式）。 */
        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        /** 压缩记忆的摘要模型（{@code COMPRESSING} 模式必填）。 */
        public Builder summaryChatModel(ChatModel summaryChatModel) {
            this.summaryChatModel = summaryChatModel;
            return this;
        }

        /** 压缩记忆触发阈值（{@code COMPRESSING} 模式）。 */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /** 压缩后保留的最近消息条数（{@code COMPRESSING} 模式）。 */
        public Builder keepRecentMessages(int keepRecentMessages) {
            this.keepRecentMessages = keepRecentMessages;
            return this;
        }

        /** 压缩摘要提示词模板（{@code COMPRESSING} 模式，可选）。 */
        public Builder summaryPrompt(String summaryPrompt) {
            this.summaryPrompt = summaryPrompt;
            return this;
        }

        /** 全量历史留痕的 advisor，直接挂载到请求链（可选）。 */
        public Builder historyAdvisor(Advisor historyAdvisor) {
            this.historyAdvisor = historyAdvisor;
            return this;
        }

        public Builder inputResolver(InputResolver<String> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<String> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public ChatGenerationStep build() {
            ChatMemory chatMemory = buildChatMemory(memoryMode, maxMessages,
                    summaryChatModel, maxTokens, keepRecentMessages, summaryPrompt);
            return new ChatGenerationStep(chatClient, systemPrompt, userPrompt, streaming,
                    chatMemory, historyAdvisor, null, inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder {
        private String apiKey;
        private String baseUrl;
        private String model = "gpt-4o-mini";
        private String systemPrompt;
        private String userPrompt;
        private boolean streaming;
        private ChatMemoryMode memoryMode = ChatMemoryMode.WINDOW;
        private int maxMessages = DEFAULT_MAX_MESSAGES;
        private ChatModel summaryChatModel;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private int keepRecentMessages = DEFAULT_KEEP_RECENT_MESSAGES;
        private String summaryPrompt;
        private Advisor historyAdvisor;
        private InputResolver<String> inputResolver = defaultInputResolver();
        private OutputResolver<String> outputResolver = new DefaultOutputResolver<>("answer");

        private ConfigurableBuilder() {}

        public ConfigurableBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ConfigurableBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ConfigurableBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ConfigurableBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public ConfigurableBuilder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public ConfigurableBuilder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public ConfigurableBuilder memoryMode(ChatMemoryMode memoryMode) {
            this.memoryMode = memoryMode;
            return this;
        }

        public ConfigurableBuilder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public ConfigurableBuilder summaryChatModel(ChatModel summaryChatModel) {
            this.summaryChatModel = summaryChatModel;
            return this;
        }

        public ConfigurableBuilder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public ConfigurableBuilder keepRecentMessages(int keepRecentMessages) {
            this.keepRecentMessages = keepRecentMessages;
            return this;
        }

        public ConfigurableBuilder summaryPrompt(String summaryPrompt) {
            this.summaryPrompt = summaryPrompt;
            return this;
        }

        public ConfigurableBuilder historyAdvisor(Advisor historyAdvisor) {
            this.historyAdvisor = historyAdvisor;
            return this;
        }

        public ConfigurableBuilder inputResolver(InputResolver<String> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder outputResolver(OutputResolver<String> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public ChatGenerationStep build() {
            OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey);
            if (baseUrl != null && !baseUrl.isEmpty()) {
                apiBuilder.baseUrl(baseUrl);
            }
            OpenAiApi openAiApi = apiBuilder.build();
            OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                    .model(model)
                    .streamUsage(true)
                    .build();
            OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, chatOptions, null, null, null);

            ChatModel effectiveSummary = (memoryMode == ChatMemoryMode.COMPRESSING && summaryChatModel == null)
                    ? chatModel
                    : summaryChatModel;
            ChatMemory chatMemory = buildChatMemory(memoryMode, maxMessages,
                    effectiveSummary, maxTokens, keepRecentMessages, summaryPrompt);

            return new ChatGenerationStep(ChatClient.create(chatModel), systemPrompt, userPrompt, streaming,
                    chatMemory, historyAdvisor, model, inputResolver, outputResolver);
        }
    }

    private static InputResolver<String> defaultInputResolver() {
        return new DefaultInputResolver.ByKeyOrDefaultResolver<>(DEFAULT_CONVERSATION_ID_KEY, String.class, null);
    }
}
