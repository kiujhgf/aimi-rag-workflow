package io.github.aimi.rag.workflow.ingest.step.embedding.resolver;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.model.TextToEmbed;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the texts to embed from every chunk into a flat list. A chunk without
 * explicit texts to embed falls back to its {@link Chunk#getContent()}.
 */
public class ChunkTextInputResolver implements InputResolver<List<String>> {

    private final String chunkKey;

    public ChunkTextInputResolver(String chunkKey) {
        this.chunkKey = chunkKey;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> resolve(FlowContext context) throws ResolveInputException {
        Object value = context.get(chunkKey);
        if (!(value instanceof List<?> list)) {
            throw new ResolveInputException("embedding", chunkKey, "No chunk list found");
        }

        List<String> texts = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Chunk chunk)) {
                throw new ResolveInputException("embedding", chunkKey,
                        "Expected Chunk element, got: " + (element == null ? "null" : element.getClass().getSimpleName()));
            }
            List<TextToEmbed> items = chunk.getTextsToEmbed();
            if (items == null || items.isEmpty()) {
                texts.add(chunk.getContent());
            } else {
                for (TextToEmbed item : items) {
                    texts.add(item.getText());
                }
            }
        }
        return texts;
    }
}
