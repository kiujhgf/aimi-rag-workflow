package io.github.aimi.rag.workflow.ingest.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTest {

    @Test
    void shouldDefaultToEmptyTextsToEmbed() {
        Chunk chunk = new Chunk("text", Map.of());

        assertTrue(chunk.getTextsToEmbed().isEmpty());
    }

    @Test
    void shouldReturnProvidedTextsToEmbedInOrder() {
        TextToEmbed t1 = new TextToEmbed(null, "text1");
        TextToEmbed t2 = new TextToEmbed("image", "text2");
        Chunk chunk = new Chunk("text", Map.of(), List.of(t1, t2));

        assertEquals(List.of(t1, t2), chunk.getTextsToEmbed());
    }

    @Test
    void shouldReturnImmutableTextsToEmbed() {
        Chunk chunk = new Chunk("text", Map.of(), List.of(new TextToEmbed("image", "text1")));

        assertThrows(UnsupportedOperationException.class,
                () -> chunk.getTextsToEmbed().add(new TextToEmbed("audio", "text2")));
    }
}
