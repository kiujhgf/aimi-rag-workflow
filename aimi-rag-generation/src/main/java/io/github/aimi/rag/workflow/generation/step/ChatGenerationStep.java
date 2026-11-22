package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.GenerationStep;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

/**
 * Provider-agnostic chat generation step.
 *
 * <p>消息组装规则：</p>
 * <ol>
 *   <li>上下文 {@code messages} 非空时，原样透传给模型，配置的 prompt 不生效</li>
 *   <li>{@code messages} 为空时，先拼 {@code systemPrompt}（SystemMessage），再拼 {@code userPrompt}（UserMessage）</li>
 *   <li>两者都为空时抛 {@link StepExecuteException}</li>
 * </ol>
 *
 * <p>两个 prompt 均支持 {@code {key}} 占位符，运行时从 {@link FlowContext} 取值替换；
 * 取不到值时保留原样，避免误伤 JSON 等含花括号的文本。</p>
 *
 * <p>需要结构化（DTO / JSON schema）输出时，使用 {@link StructuredChatGenerationStep}。</p>
 *
 * <p>流式输出：通过 builder 配置 {@link Builder#streaming(boolean)} 后，本 Step 不再调用模型，
 * 而是把 {@code stream().content()} 返回的冷 {@code Flux<String>} 写入上下文 key
 * {@link #STREAM_KEY}，由下游节点自行订阅处理（订阅时才真正发起请求）；
 * 该模式下不写默认的 {@code answer}。未开启时仍走一次性的 {@code call()}。</p>
 */
public class ChatGenerationStep extends GenerationStep {

    private static final String STEP_NAME = "chat-generation";

    /** 流式模式下写入上下文的 key。 */
    public static final String STREAM_KEY = "stream";

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final String userPrompt;
    private final boolean streaming;

    private ChatGenerationStep(ChatClient chatClient,
                               String systemPrompt,
                               String userPrompt,
                               boolean streaming,
                               InputResolver<List<Message>> inputResolver,
                               OutputResolver<String> outputResolver) {
        super(inputResolver, outputResolver);
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.streaming = streaming;
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
    protected String doProcess(List<Message> input, FlowContext context) throws StepExecuteException {
        List<Message> messages = ChatMessageAssembler.resolve(input, systemPrompt, userPrompt, context, STEP_NAME);
        if (!streaming) {
            return chatClient.prompt().messages(messages).call().content();
        }

        context.set(STREAM_KEY, chatClient.prompt().messages(messages).stream().content());
        return null;
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

    public static class Builder {
        private final ChatClient chatClient;
        private String systemPrompt;
        private String userPrompt;
        private boolean streaming;
        private InputResolver<List<Message>> inputResolver = ChatMessageAssembler.defaultInputResolver();
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

        public Builder inputResolver(InputResolver<List<Message>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<String> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public ChatGenerationStep build() {
            return new ChatGenerationStep(chatClient, systemPrompt, userPrompt, streaming,
                    inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder {
        private String apiKey;
        private String baseUrl;
        private String model = "gpt-4o-mini";
        private String systemPrompt;
        private String userPrompt;
        private boolean streaming;
        private InputResolver<List<Message>> inputResolver = ChatMessageAssembler.defaultInputResolver();
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

        public ConfigurableBuilder inputResolver(InputResolver<List<Message>> inputResolver) {
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
                    .build();
            OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, chatOptions, null, null, null);
            return new ChatGenerationStep(ChatClient.create(chatModel), systemPrompt, userPrompt, streaming,
                    inputResolver, outputResolver);
        }
    }
}
