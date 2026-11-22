package io.github.aimi.rag.workflow.ingest.step.embedding.resolver;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.model.TextToEmbed;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTextInputResolverTest {

    private final ChunkTextInputResolver resolver = new ChunkTextInputResolver("chunks");

    @Test
    void shouldCollectTextsFromChunksInOrder() {
        Chunk c1 = new Chunk("content1", new HashMap<>(),
                List.of(new TextToEmbed(null, "主1"), new TextToEmbed("image", "图1")));
        Chunk c2 = new Chunk("content2", new HashMap<>(),
                List.of(new TextToEmbed(null, "主2")));
        FlowContext context = FlowContext.create().set("chunks", List.of(c1, c2));

        List<String> texts = resolver.resolve(context);

        assertEquals(List.of("主1", "图1", "主2"), texts);
    }

    @Test
    void shouldUseContentWhenNoTextsToEmbed() {
        Chunk chunk = new Chunk("content", new HashMap<>());
        FlowContext context = FlowContext.create().set("chunks", List.of(chunk));

        List<String> texts = resolver.resolve(context);

        assertEquals(List.of("content"), texts);
    }

    @Test
    void shouldThrowWhenChunkKeyMissing() {
        FlowContext context = FlowContext.create();

        assertThrows(ResolveInputException.class, () -> resolver.resolve(context));
    }

    @Test
    void shouldThrowWhenElementIsNotChunk() {
        FlowContext context = FlowContext.create().set("chunks", List.of("not-a-chunk"));

        assertThrows(ResolveInputException.class, () -> resolver.resolve(context));
    }
}
