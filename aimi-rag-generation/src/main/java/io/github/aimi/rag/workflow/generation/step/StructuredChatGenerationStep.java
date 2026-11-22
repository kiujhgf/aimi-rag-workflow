package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持结构化输出的聊天生成步骤，通过 {@link ChatClient#prompt()} 的 {@code entity()}
 * 把模型输出转换为指定类型（默认使用 {@link BeanOutputConverter}），
 * JSON schema 格式指令由 ChatClient 自动追加到用户消息。
 *
 * <p>消息组装规则与 {@link ChatGenerationStep} 一致：{@code messages} 非空时透传，
 * 否则由 systemPrompt / userPrompt 组装（支持 {@code {key}} 占位符）。</p>
 *
 * <p>转换结果写入 {@link FlowContext}，默认 key 为 {@code structured}。
 * 转换失败抛 {@link StepExecuteException}。</p>
 *
 * <p>流式输出：该 Step 内部始终使用 {@code stream()} 调用模型，等待全部分片返回后
 * 再交给 converter 转换。由于 {@code StreamResponseSpec} 没有 {@code entity()} 重载，
 * 格式指令由本类手动追加到最后一条用户消息。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * StructuredChatGenerationStep<Person> step =
 *         StructuredChatGenerationStep.builder(chatClient, Person.class).build();
 *
 * StructuredChatGenerationStep<List<Person>> listStep =
 *         StructuredChatGenerationStep.builder(chatClient, new ParameterizedTypeReference<List<Person>>() {})
 *                 .userPrompt("列出 {count} 个人物")
 *                 .build();
 * }</pre>
 *
 * @param <T> 结构化输出类型
 */
public class StructuredChatGenerationStep<T> extends AbstractStep<List<Message>, T> {

    private static final String STEP_NAME = "structured-chat-generation";

    private final ChatClient chatClient;
    private final StructuredOutputConverter<T> outputConverter;
    private final String systemPrompt;
    private final String userPrompt;

    private StructuredChatGenerationStep(ChatClient chatClient,
                                         StructuredOutputConverter<T> outputConverter,
                                         String systemPrompt,
                                         String userPrompt,
                                         InputResolver<List<Message>> inputResolver,
                                         OutputResolver<T> outputResolver) {
        super(inputResolver, outputResolver);
        this.chatClient = chatClient;
        this.outputConverter = outputConverter;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    public static <T> Builder<T> builder(ChatClient chatClient, Class<T> outputType) {
        return new Builder<>(chatClient, new BeanOutputConverter<>(outputType));
    }

    public static <T> Builder<T> builder(ChatClient chatClient, ParameterizedTypeReference<T> outputType) {
        return new Builder<>(chatClient, new BeanOutputConverter<>(outputType));
    }

    public static <T> Builder<T> builder(ChatClient chatClient, StructuredOutputConverter<T> outputConverter) {
        return new Builder<>(chatClient, outputConverter);
    }

    public static <T> ConfigurableBuilder<T> builder(Class<T> outputType) {
        return new ConfigurableBuilder<>(new BeanOutputConverter<>(outputType));
    }

    public static <T> ConfigurableBuilder<T> builder(ParameterizedTypeReference<T> outputType) {
        return new ConfigurableBuilder<>(new BeanOutputConverter<>(outputType));
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
    @SuppressWarnings("unchecked")
    public Class<List<Message>> getInputType() {
        return (Class<List<Message>>) (Class<?>) List.class;
    }

    @Override
    protected T doProcess(List<Message> input, FlowContext context) throws StepExecuteException {
        List<Message> messages = ChatMessageAssembler.resolve(input, systemPrompt, userPrompt, context, STEP_NAME);
        try {
            List<Message> streamingMessages = appendFormatInstructions(messages, outputConverter.getFormat());
            StringBuilder raw = new StringBuilder();
            chatClient.prompt().messages(streamingMessages).stream().content()
                    .doOnNext(raw::append)
                    .blockLast();
            return outputConverter.convert(raw.toString());
        } catch (RuntimeException e) {
            throw new StepExecuteException(STEP_NAME, "Failed to generate structured output", e);
        }
    }

    /**
     * 流式模式下 {@code StreamResponseSpec} 没有 {@code entity()} 重载，无法自动注入格式指令，
     * 因此按与 ChatClient 一致的规则追加到最后一条用户消息末尾。
     */
    private static List<Message> appendFormatInstructions(List<Message> messages, String format) {
        if (format == null || format.isBlank()) {
            return messages;
        }

        List<Message> augmented = new ArrayList<>(messages);
        for (int i = augmented.size() - 1; i >= 0; i--) {
            if (augmented.get(i) instanceof UserMessage userMessage) {
                String text = userMessage.getText();
                String augmentedText = (text == null || text.isEmpty())
                        ? format
                        : text + System.lineSeparator() + format;
                augmented.set(i, userMessage.mutate().text(augmentedText).build());
                return augmented;
            }
        }
        augmented.add(new UserMessage(format));
        return augmented;
    }

    public StructuredOutputConverter<T> getOutputConverter() {
        return outputConverter;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public static class Builder<T> {
        private final ChatClient chatClient;
        private final StructuredOutputConverter<T> outputConverter;
        private String systemPrompt;
        private String userPrompt;
        private InputResolver<List<Message>> inputResolver = ChatMessageAssembler.defaultInputResolver();
        private OutputResolver<T> outputResolver = new DefaultOutputResolver<>("structured");

        private Builder(ChatClient chatClient, StructuredOutputConverter<T> outputConverter) {
            this.chatClient = chatClient;
            this.outputConverter = outputConverter;
        }

        public Builder<T> systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder<T> userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder<T> inputResolver(InputResolver<List<Message>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder<T> outputResolver(OutputResolver<T> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public StructuredChatGenerationStep<T> build() {
            return new StructuredChatGenerationStep<>(chatClient, outputConverter, systemPrompt, userPrompt,
                    inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder<T> {
        private final StructuredOutputConverter<T> outputConverter;
        private String apiKey;
        private String baseUrl;
        private String model = "gpt-4o-mini";
        private String systemPrompt;
        private String userPrompt;
        private InputResolver<List<Message>> inputResolver = ChatMessageAssembler.defaultInputResolver();
        private OutputResolver<T> outputResolver = new DefaultOutputResolver<>("structured");

        private ConfigurableBuilder(StructuredOutputConverter<T> outputConverter) {
            this.outputConverter = outputConverter;
        }

        public ConfigurableBuilder<T> apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ConfigurableBuilder<T> baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ConfigurableBuilder<T> model(String model) {
            this.model = model;
            return this;
        }

        public ConfigurableBuilder<T> systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public ConfigurableBuilder<T> userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public ConfigurableBuilder<T> inputResolver(InputResolver<List<Message>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder<T> outputResolver(OutputResolver<T> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public StructuredChatGenerationStep<T> build() {
            OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey);
            if (baseUrl != null && !baseUrl.isEmpty()) {
                apiBuilder.baseUrl(baseUrl);
            }
            OpenAiApi openAiApi = apiBuilder.build();
            OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                    .model(model)
                    .build();
            OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, chatOptions, null, null, null);
            return new StructuredChatGenerationStep<>(ChatClient.create(chatModel), outputConverter,
                    systemPrompt, userPrompt, inputResolver, outputResolver);
        }
    }
}
