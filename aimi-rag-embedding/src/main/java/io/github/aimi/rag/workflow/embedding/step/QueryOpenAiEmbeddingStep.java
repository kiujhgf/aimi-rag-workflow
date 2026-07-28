package io.github.aimi.rag.workflow.embedding.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.query.step.embedding.QueryEmbeddingStep;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

public class QueryOpenAiEmbeddingStep extends QueryEmbeddingStep {

    private final OpenAiEmbeddingBaseStep baseStep;

    private QueryOpenAiEmbeddingStep(EmbeddingModel embeddingModel, InputResolver<String> inputResolver, OutputResolver<float[]> outputResolver) {
        super(inputResolver, outputResolver);
        this.baseStep = new OpenAiEmbeddingBaseStep(embeddingModel) {};
    }

    public static Builder builder(EmbeddingModel embeddingModel) {
        return new Builder(embeddingModel);
    }

    public static ConfigurableBuilder builder() {
        return new ConfigurableBuilder();
    }

    @Override
    public String getName() {
        return "query-open-ai-embedding";
    }

    @Override
    protected float[] doProcess(String input, FlowContext context) throws StepExecuteException {
        List<float[]> vectors = baseStep.batchEmbed(List.of(input), 1);
        return vectors.get(0);
    }

    public static class Builder {
        private final EmbeddingModel embeddingModel;
        private InputResolver<String> inputResolver = new DefaultInputResolver.ByKeyResolver<>("query_text", String.class, "query-open-ai-embedding");
        private OutputResolver<float[]> outputResolver = new DefaultOutputResolver<>("query_vector");

        private Builder(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public Builder inputResolver(InputResolver<String> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<float[]> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public QueryOpenAiEmbeddingStep build() {
            return new QueryOpenAiEmbeddingStep(embeddingModel, inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder {
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer dimensions;
        private InputResolver<String> inputResolver = new DefaultInputResolver.ByKeyResolver<>("query_text", String.class, "query-open-ai-embedding");
        private OutputResolver<float[]> outputResolver = new DefaultOutputResolver<>("query_vector");

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

        public ConfigurableBuilder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public ConfigurableBuilder inputResolver(InputResolver<String> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder outputResolver(OutputResolver<float[]> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public QueryOpenAiEmbeddingStep build() {
            EmbeddingModel embeddingModel = new OpenAiEmbeddingBaseStep(null) {}.createEmbeddingModel(apiKey, baseUrl, model, dimensions);
            return new QueryOpenAiEmbeddingStep(embeddingModel, inputResolver, outputResolver);
        }
    }
}
