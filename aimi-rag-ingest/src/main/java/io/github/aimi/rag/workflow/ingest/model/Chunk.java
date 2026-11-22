package io.github.aimi.rag.workflow.ingest.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A text chunk with optional metadata, the core unit of data flowing through the ingest pipeline.
 */
public class Chunk implements StorageItem {

    private final String content;
    private final Map<String, Object> metadata;
    private final List<TextToEmbed> textsToEmbed;

    public Chunk(String content, Map<String, Object> metadata) {
        this(content, metadata, List.of());
    }

    public Chunk(String content, Map<String, Object> metadata, List<TextToEmbed> textsToEmbed) {
        this.content = content;
        // 可变副本,供 embedding step 写回额外向量
        this.metadata = new HashMap<>(metadata);
        this.textsToEmbed = List.copyOf(textsToEmbed);
    }

    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<TextToEmbed> getTextsToEmbed() { return textsToEmbed; }
}
