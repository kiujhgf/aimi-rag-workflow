package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.query.model.QueryResult;
import io.github.aimi.rag.workflow.query.model.RetrievalResult;
import io.github.aimi.rag.workflow.query.model.SearchResult;
import io.github.aimi.rag.workflow.query.step.generation.GenerationStep;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiGenerationStep extends GenerationStep {

    private final ChatModel chatModel;
    private final String systemPrompt;
    private final String userPromptTemplate;

    private OpenAiGenerationStep(ChatModel chatModel, String systemPrompt, String userPromptTemplate,
                                  InputResolver<RetrievalResult> inputResolver,
                                  OutputResolver<QueryResult> outputResolver) {
        super(inputResolver, outputResolver);
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
    }

    public static Builder builder(ChatModel chatModel) {
        return new Builder(chatModel);
    }

    public static ConfigurableBuilder builder() {
        return new ConfigurableBuilder();
    }

    @Override
    public String getName() {
        return "open-ai-generation";
    }

    @Override
    protected QueryResult doProcess(RetrievalResult retrievalResult, FlowContext context) throws StepExecuteException {
        String queryText = context.get("query_text", String.class);
        
        String contextContent = retrievalResult.getContextContent();

        Map<String, Object> promptVariables = new HashMap<>();
        promptVariables.put("context", contextContent);
        promptVariables.put("question", queryText);

        var systemMessage = new SystemPromptTemplate(systemPrompt).createMessage(promptVariables);
        
        String userPrompt = userPromptTemplate.replace("{context}", contextContent)
                                              .replace("{question}", queryText);
        var userMessage = new UserMessage(userPrompt);

        ChatResponse response = chatModel.call(new Prompt(List.of(systemMessage, userMessage)));
        String answer = response.getResult().getOutput().getText();

        return new QueryResult(answer, retrievalResult);
    }

    public static class Builder {
        private final ChatModel chatModel;
        private String systemPrompt = "你是一个知识问答助手，根据提供的上下文信息回答用户的问题。只使用上下文中的信息，如果上下文没有相关信息，请明确说明。";
        private String userPromptTemplate = "上下文信息：\n{context}\n\n用户问题：{question}\n\n请根据上下文回答用户的问题：";
        private InputResolver<RetrievalResult> inputResolver = new DefaultInputResolver.ByKeyResolver<>("retrieval_result", RetrievalResult.class, "open-ai-generation");
        private OutputResolver<QueryResult> outputResolver = new DefaultOutputResolver<>("query_result");

        private Builder(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPromptTemplate(String userPromptTemplate) {
            this.userPromptTemplate = userPromptTemplate;
            return this;
        }

        public Builder inputResolver(InputResolver<RetrievalResult> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<QueryResult> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public OpenAiGenerationStep build() {
            return new OpenAiGenerationStep(chatModel, systemPrompt, userPromptTemplate, inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder {
        private String apiKey;
        private String baseUrl;
        private String model = "gpt-4o-mini";
        private String systemPrompt = "你是一个知识问答助手，根据提供的上下文信息回答用户的问题。只使用上下文中的信息，如果上下文没有相关信息，请明确说明。";
        private String userPromptTemplate = "上下文信息：\n{context}\n\n用户问题：{question}\n\n请根据上下文回答用户的问题：";
        private InputResolver<RetrievalResult> inputResolver = new DefaultInputResolver.ByKeyResolver<>("retrieval_result", RetrievalResult.class, "open-ai-generation");
        private OutputResolver<QueryResult> outputResolver = new DefaultOutputResolver<>("query_result");

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

        public ConfigurableBuilder userPromptTemplate(String userPromptTemplate) {
            this.userPromptTemplate = userPromptTemplate;
            return this;
        }

        public ConfigurableBuilder inputResolver(InputResolver<RetrievalResult> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder outputResolver(OutputResolver<QueryResult> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public OpenAiGenerationStep build() {
            OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey);
            if (baseUrl != null && !baseUrl.isEmpty()) {
                apiBuilder.baseUrl(baseUrl);
            }
            var openAiApi = apiBuilder.build();
            var chatOptions = OpenAiChatOptions.builder()
                    .model(model)
                    .build();
            var chatModel = new OpenAiChatModel(openAiApi, chatOptions, null, null, null);
            return new OpenAiGenerationStep(chatModel, systemPrompt, userPromptTemplate, inputResolver, outputResolver);
        }
    }
}