package io.github.aimi.rag.workflow.embedding.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;
import java.util.stream.IntStream;

public abstract class OpenAiEmbeddingBaseStep {

    protected final EmbeddingModel embeddingModel;

    protected OpenAiEmbeddingBaseStep(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    protected List<float[]> batchEmbed(List<String> texts, int batchSize) throws StepExecuteException {
        int effectiveBatchSize = Math.max(1, batchSize);
        return IntStream.range(0, (texts.size() + effectiveBatchSize - 1) / effectiveBatchSize)
                .mapToObj(batchIndex -> {
                    int start = batchIndex * effectiveBatchSize;
                    int end = Math.min(start + effectiveBatchSize, texts.size());
                    List<String> batchTexts = texts.subList(start, end);
                    var response = embeddingModel.embedForResponse(batchTexts);
                    return IntStream.range(0, batchTexts.size())
                            .mapToObj(i -> response.getResults().get(i).getOutput())
                            .toList();
                })
                .flatMap(List::stream)
                .toList();
    }

    protected EmbeddingModel createEmbeddingModel(String apiKey, String baseUrl, String model, Integer dimensions) {
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

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
