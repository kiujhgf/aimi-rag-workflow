package io.github.aimi.rag.workflow.ingest.step.embedding.resolver;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.model.Embedding;
import io.github.aimi.rag.workflow.ingest.model.TextToEmbed;

import java.util.ArrayList;
import java.util.List;

/**
 * Distributes embedding vectors back to chunks: the first empty-name vector becomes the
 * primary vector, each non-empty-name vector is written to metadata as {@code <name>_vector}.
 * A chunk without texts to embed falls back to its content as the primary vector.
 */
public class ChunkEmbeddingOutputResolver implements OutputResolver<List<float[]>> {

    private final String chunkKey;
    private final String embeddingKey;

    public ChunkEmbeddingOutputResolver(String chunkKey, String embeddingKey) {
        this.chunkKey = chunkKey;
        this.embeddingKey = embeddingKey;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void resolve(List<float[]> vectors, FlowContext context) {
        Object value = context.get(chunkKey);
        if (!(value instanceof List<?> list)) {
            context.set(embeddingKey, List.of());
            return;
        }

        int index = 0;
        List<Embedding> embeddings = new ArrayList<>(list.size());
        for (Object element : list) {
            Chunk chunk = (Chunk) element;
            float[] primary = null;
            List<TextToEmbed> items = chunk.getTextsToEmbed();
            if (items == null || items.isEmpty()) {
                primary = vectors.get(index++);
            } else {
                for (TextToEmbed item : items) {
                    float[] vector = vectors.get(index++);
                    if (item.getName() == null || item.getName().isEmpty()) {
                        if (primary == null) {
                            primary = vector;
                        }
                    } else {
                        chunk.getMetadata().put(item.getName() + "_vector", toFloatList(vector));
                    }
                }
            }
            embeddings.add(new Embedding(chunk, primary));
        }
        context.set(embeddingKey, embeddings);
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float f : vector) {
            result.add(f);
        }
        return result;
    }
}
