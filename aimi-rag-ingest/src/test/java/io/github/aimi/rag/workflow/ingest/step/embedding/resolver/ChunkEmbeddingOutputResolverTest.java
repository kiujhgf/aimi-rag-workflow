package io.github.aimi.rag.workflow.ingest.step.embedding.resolver;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.model.Embedding;
import io.github.aimi.rag.workflow.ingest.model.TextToEmbed;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkEmbeddingOutputResolverTest {

    private final ChunkEmbeddingOutputResolver resolver = new ChunkEmbeddingOutputResolver("chunks", "embeddings");

    @Test
    void shouldSetPrimaryVectorFromEmptyName() {
        Chunk chunk = new Chunk("content", new HashMap<>(),
                List.of(new TextToEmbed(null, "主")));
        FlowContext context = FlowContext.create().set("chunks", List.of(chunk));

        resolver.resolve(List.of(new float[]{1.0f, 2.0f}), context);

        List<Embedding> embeddings = context.get("embeddings");
        assertArrayEquals(new float[]{1.0f, 2.0f}, embeddings.get(0).getVector());
    }

    @Test
    void shouldWriteAdditionalVectorToMetadata() {
        Chunk chunk = new Chunk("content", new HashMap<>(),
                List.of(new TextToEmbed(null, "主"), new TextToEmbed("image", "图")));
        FlowContext context = FlowContext.create().set("chunks", List.of(chunk));

        resolver.resolve(List.of(new float[]{1.0f}, new float[]{2.0f, 3.0f}), context);

        List<Embedding> embeddings = context.get("embeddings");
        assertArrayEquals(new float[]{1.0f}, embeddings.get(0).getVector());
        assertEquals(List.of(2.0f, 3.0f), chunk.getMetadata().get("image_vector"));
    }

    @Test
    void shouldSetPrimaryFromContentWhenNoTextsToEmbed() {
        Chunk chunk = new Chunk("content", new HashMap<>());
        FlowContext context = FlowContext.create().set("chunks", List.of(chunk));

        resolver.resolve(List.of(new float[]{1.0f, 2.0f}), context);

        List<Embedding> embeddings = context.get("embeddings");
        assertArrayEquals(new float[]{1.0f, 2.0f}, embeddings.get(0).getVector());
    }

    @Test
    void shouldSetEmptyListWhenChunkKeyMissing() {
        FlowContext context = FlowContext.create();

        resolver.resolve(List.of(new float[]{1.0f}), context);

        List<Embedding> embeddings = context.get("embeddings");
        assertTrue(embeddings.isEmpty());
    }
}
