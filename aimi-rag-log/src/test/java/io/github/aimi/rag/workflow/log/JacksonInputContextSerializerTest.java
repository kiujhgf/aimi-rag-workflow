package io.github.aimi.rag.workflow.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JacksonInputContextSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldOnlySerializeIncludedKeys() throws Exception {
        FlowContext context = FlowContext.create();
        context.set("question", "How are you?");
        context.set("userId", 42);
        context.set("messages", List.of("private history"));
        context.set("answer", "secret");
        JacksonInputContextSerializer serializer = new JacksonInputContextSerializer(
                List.of("question", "userId", "missing"));

        JsonNode json = objectMapper.readTree(serializer.serialize(context));

        assertEquals("How are you?", json.get("question").asText());
        assertEquals(42, json.get("userId").asInt());
        assertFalse(json.has("messages"));
        assertFalse(json.has("answer"));
        assertFalse(json.has("missing"));
    }

    @Test
    void shouldSkipOnlyTheUnserializableValue() throws Exception {
        FlowContext context = FlowContext.create();
        context.set("question", "kept");
        context.set("bad", new Object());
        JacksonInputContextSerializer serializer = new JacksonInputContextSerializer(
                List.of("question", "bad"));

        JsonNode json = objectMapper.readTree(serializer.serialize(context));

        assertEquals("kept", json.get("question").asText());
        assertFalse(json.has("bad"));
    }

    @Test
    void shouldWriteTruncationMarkerInsteadOfPartialJson() {
        FlowContext context = FlowContext.create();
        context.set("question", "x".repeat(200));
        JacksonInputContextSerializer serializer = new JacksonInputContextSerializer(
                List.of("question"), 32);

        assertEquals(JacksonInputContextSerializer.TRUNCATED_JSON, serializer.serialize(context));
    }

    @Test
    void shouldProvideNoSnapshotByDefault() {
        assertNull(InputContextSerializer.none().serialize(FlowContext.create()));
    }
}
