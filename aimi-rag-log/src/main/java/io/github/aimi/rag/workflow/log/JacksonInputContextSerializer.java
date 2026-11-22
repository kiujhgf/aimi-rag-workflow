package io.github.aimi.rag.workflow.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class JacksonInputContextSerializer implements InputContextSerializer {

    public static final int DEFAULT_MAX_CHARS = 16 * 1024;
    public static final String TRUNCATED_JSON = "{\"truncated\":true}";

    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonInputContextSerializer.class);

    private final ObjectMapper objectMapper;
    private final Set<String> includeKeys;
    private final int maxChars;

    public JacksonInputContextSerializer(Collection<String> includeKeys) {
        this(new ObjectMapper(), includeKeys, DEFAULT_MAX_CHARS);
    }

    public JacksonInputContextSerializer(Collection<String> includeKeys, int maxChars) {
        this(new ObjectMapper(), includeKeys, maxChars);
    }

    public JacksonInputContextSerializer(ObjectMapper objectMapper,
                                         Collection<String> includeKeys, int maxChars) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.includeKeys = new LinkedHashSet<>(
                Objects.requireNonNull(includeKeys, "includeKeys must not be null"));
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be greater than zero");
        }
        this.maxChars = maxChars;
    }

    @Override
    public String serialize(FlowContext context) {
        if (context == null) {
            return null;
        }

        ObjectNode snapshot = objectMapper.createObjectNode();
        for (String key : includeKeys) {
            if (!context.contains(key)) {
                continue;
            }
            try {
                JsonNode value = objectMapper.valueToTree(context.get(key));
                snapshot.set(key, value);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping input context key '{}' because its value cannot be serialized", key, e);
            }
        }

        try {
            String json = objectMapper.writeValueAsString(snapshot);
            if (json.length() > maxChars) {
                LOGGER.warn("Input context snapshot exceeds {} characters; storing truncation marker", maxChars);
                return TRUNCATED_JSON;
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize input context snapshot", e);
        }
    }
}
