package io.github.aimi.rag.workflow.embedding.step;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.model.Embedding;
import io.github.aimi.rag.workflow.ingest.model.TextToEmbed;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class IngestOpenAiEmbeddingStepTest {

    @Test
    void shouldEmbedPrimaryVectorFromEmptyName() throws Exception {
        EmbeddingModel model = mock(EmbeddingModel.class);
        EmbeddingResponse response = mock(EmbeddingResponse.class);
        org.springframework.ai.embedding.Embedding aiEmbedding = mock(org.springframework.ai.embedding.Embedding.class);
        when(aiEmbedding.getOutput()).thenReturn(new float[]{1.0f, 2.0f});
        when(response.getResults()).thenReturn(List.of(aiEmbedding));
        when(model.embedForResponse(anyList())).thenReturn(response);

        IngestOpenAiEmbeddingStep step = IngestOpenAiEmbeddingStep.builder(model).build();
        Chunk chunk = new Chunk("content", new HashMap<>(), List.of(new TextToEmbed(null, "主文本")));

        FlowContext context = FlowContext.create().set("chunks", List.of(chunk));
        step.process(context);

        List<Embedding> embeddings = context.get("embeddings");
        assertNotNull(embeddings);
        assertArrayEquals(new float[]{1.0f, 2.0f}, embeddings.get(0).getVector());
        verify(model).embedForResponse(List.of("主文本"));
    }

    @Test
    void shouldWriteAdditionalVectorToMetadata() throws Exception {
        EmbeddingModel model = mock(EmbeddingModel.class);
        EmbeddingResponse response = mock(EmbeddingResponse.class);
        org.springframework.ai.embedding.Embedding primary = mock(org.springframework.ai.embedding.Embedding.class);
        org.springframework.ai.embedding.Embedding image = mock(org.springframework.ai.embedding.Embedding.class);
        when(primary.getOutput()).thenReturn(new float[]{1.0f});
        when(image.getOutput()).thenReturn(new float[]{2.0f, 3.0f});
        when(response.getResults()).thenReturn(List.of(primary, image));
        when(model.embedForResponse(anyList())).thenReturn(response);

        IngestOpenAiEmbeddingStep step = IngestOpenAiEmbeddingStep.builder(model).build();
        Chunk chunk = new Chunk("content", new HashMap<>(),
                List.of(new TextToEmbed(null, "主"), new TextToEmbed("image", "图片描述")));

        FlowContext context = FlowContext.create().set("chunks", List.of(chunk));
        step.process(context);

        List<Embedding> embeddings = context.get("embeddings");
        assertArrayEquals(new float[]{1.0f}, embeddings.get(0).getVector());
        assertEquals(List.of(2.0f, 3.0f), chunk.getMetadata().get("image_vector"));
        verify(model).embedForResponse(List.of("主", "图片描述"));
    }

    @Test
    void shouldEmbedContentWhenNoTextsToEmbed() throws Exception {
        EmbeddingModel model = mock(EmbeddingModel.class);
        EmbeddingResponse response = mock(EmbeddingResponse.class);
        org.springframework.ai.embedding.Embedding aiEmbedding = mock(org.springframework.ai.embedding.Embedding.class);
        when(aiEmbedding.getOutput()).thenReturn(new float[]{1.0f, 2.0f});
        when(response.getResults()).thenReturn(List.of(aiEmbedding));
        when(model.embedForResponse(anyList())).thenReturn(response);

        IngestOpenAiEmbeddingStep step = IngestOpenAiEmbeddingStep.builder(model).build();
        Chunk chunk = new Chunk("content", new HashMap<>());

        FlowContext context = FlowContext.create().set("chunks", List.of(chunk));
        step.process(context);

        List<Embedding> embeddings = context.get("embeddings");
        assertArrayEquals(new float[]{1.0f, 2.0f}, embeddings.get(0).getVector());
        verify(model).embedForResponse(List.of("content"));
    }
}
