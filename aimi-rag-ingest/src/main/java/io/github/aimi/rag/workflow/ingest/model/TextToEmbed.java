package io.github.aimi.rag.workflow.ingest.model;

/**
 * A named text to be embedded for a {@link Chunk}.
 * An empty or null {@code name} denotes the primary vector (mapped to {@code _vector});
 * a non-empty name denotes an additional vector (mapped to {@code <name>_vector}).
 * The {@code text} is embedded through the embedding model at ingest time.
 */
public class TextToEmbed {

    private final String name;
    private final String text;

    public TextToEmbed(String name, String text) {
        this.name = name;
        this.text = text;
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return text;
    }
}
