package io.github.aimi.rag.workflow.embedding.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.model.Embedding;
import io.github.aimi.rag.workflow.ingest.step.embedding.IngestEmbeddingStep;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;
import java.util.stream.IntStream;

public class IngestOpenAiEmbeddingStep extends IngestEmbeddingStep {

    private final EmbeddingModel embeddingModel;

    private IngestOpenAiEmbeddingStep(EmbeddingModel embeddingModel, InputResolver<List<Chunk>> inputResolver, OutputResolver<List<Embedding>> outputResolver) {
        super(inputResolver, outputResolver);
        this.embeddingModel = embeddingModel;
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
    protected List<Embedding> doProcess(List<Chunk> input, FlowContext context) throws StepExecuteException {
        List<String> texts = input.stream().map(Chunk::getContent).toList();
        var response = embeddingModel.embedForResponse(texts);
        return IntStream.range(0, input.size())
                .mapToObj(i -> new Embedding(
                        input.get(i),
                        response.getResults().get(i).getOutput()))
                .toList();
    }

    public static class Builder {
        private final EmbeddingModel embeddingModel;
        private InputResolver<List<Chunk>> inputResolver = new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "open-ai-embedding");
        private OutputResolver<List<Embedding>> outputResolver = new DefaultOutputResolver<>("embeddings");

        private Builder(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public Builder inputResolver(InputResolver<List<Chunk>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<List<Embedding>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public IngestOpenAiEmbeddingStep build() {
            return new IngestOpenAiEmbeddingStep(embeddingModel, inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder {
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer dimensions;
        private InputResolver<List<Chunk>> inputResolver = new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "open-ai-embedding");
        private OutputResolver<List<Embedding>> outputResolver = new DefaultOutputResolver<>("embeddings");

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

        public ConfigurableBuilder inputResolver(InputResolver<List<Chunk>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder outputResolver(OutputResolver<List<Embedding>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public IngestOpenAiEmbeddingStep build() {
            OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey);
            if (baseUrl != null && !baseUrl.isEmpty()) {
                apiBuilder.baseUrl(baseUrl);
            }
            OpenAiApi openAiApi = apiBuilder.build();

            OpenAiEmbeddingOptions options = null;
            if (model != null || dimensions != null) {
                var optionsBuilder = OpenAiEmbeddingOptions.builder();
                if (model != null && !model.isEmpty()) {
                    optionsBuilder.model(model);
                }
                if (dimensions != null) {
                    optionsBuilder.dimensions(dimensions);
                }
                options = optionsBuilder.build();
            }

            EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);

            return new IngestOpenAiEmbeddingStep(embeddingModel, inputResolver, outputResolver);
        }
    }
}