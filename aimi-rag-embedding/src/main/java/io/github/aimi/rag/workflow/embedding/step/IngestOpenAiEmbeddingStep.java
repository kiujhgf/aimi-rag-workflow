package io.github.aimi.rag.workflow.embedding.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.ingest.step.embedding.IngestEmbeddingStep;
import io.github.aimi.rag.workflow.ingest.step.embedding.resolver.ChunkEmbeddingOutputResolver;
import io.github.aimi.rag.workflow.ingest.step.embedding.resolver.ChunkTextInputResolver;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

public class IngestOpenAiEmbeddingStep extends IngestEmbeddingStep {

    private final OpenAiEmbeddingBaseStep baseStep;
    private final int batchSize;

    private IngestOpenAiEmbeddingStep(EmbeddingModel embeddingModel, int batchSize, InputResolver<List<String>> inputResolver, OutputResolver<List<float[]>> outputResolver) {
        super(inputResolver, outputResolver);
        this.baseStep = new OpenAiEmbeddingBaseStep(embeddingModel) {};
        this.batchSize = batchSize;
    }

    public static Builder builder(EmbeddingModel embeddingModel) {
        return new Builder(embeddingModel);
    }

    public static ConfigurableBuilder builder() {
        return new ConfigurableBuilder();
    }

    @Override
    public String getName() {
        return "open-ai-embedding";
    }

    @Override
    protected List<float[]> doProcess(List<String> input, FlowContext context) throws StepExecuteException {
        return baseStep.batchEmbed(input, batchSize);
    }

    public static class Builder {
        private final EmbeddingModel embeddingModel;
        private int batchSize = 10;
        private InputResolver<List<String>> inputResolver = new ChunkTextInputResolver("chunks");
        private OutputResolver<List<float[]>> outputResolver = new ChunkEmbeddingOutputResolver("chunks", "embeddings");

        private Builder(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder inputResolver(InputResolver<List<String>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<List<float[]>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public IngestOpenAiEmbeddingStep build() {
            return new IngestOpenAiEmbeddingStep(embeddingModel, batchSize, inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder {
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer dimensions;
        private int batchSize = 10;
        private InputResolver<List<String>> inputResolver = new ChunkTextInputResolver("chunks");
        private OutputResolver<List<float[]>> outputResolver = new ChunkEmbeddingOutputResolver("chunks", "embeddings");

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

        public ConfigurableBuilder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public ConfigurableBuilder inputResolver(InputResolver<List<String>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder outputResolver(OutputResolver<List<float[]>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public IngestOpenAiEmbeddingStep build() {
            EmbeddingModel embeddingModel = new OpenAiEmbeddingBaseStep(null) {}.createEmbeddingModel(apiKey, baseUrl, model, dimensions);
            return new IngestOpenAiEmbeddingStep(embeddingModel, batchSize, inputResolver, outputResolver);
        }
    }
}
