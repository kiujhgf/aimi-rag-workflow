package io.github.aimi.rag.workflow.ingest.step.chunking;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.ingest.model.Chunk;

import java.util.ArrayList;
import java.util.List;

public class RecursiveCharacterChunkingStep extends ChunkingStep {

    private static final List<String> DEFAULT_SEPARATORS = List.of("\n\n", "\n", " ", "");

    private final int chunkSize;
    private final int overlap;
    private final List<String> separators;

    private RecursiveCharacterChunkingStep(int chunkSize, int overlap, List<String> separators, InputResolver<List<String>> inputResolver, OutputResolver<List<Chunk>> outputResolver) {
        super(inputResolver, outputResolver);
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.separators = List.copyOf(separators);
    }

    public static Builder builder(int chunkSize) {
        return new Builder(chunkSize);
    }

    @Override
    public String getName() {
        return "recursive-chunking";
    }

    @Override
    protected List<Chunk> doProcess(List<String> input, FlowContext context) throws StepExecuteException {
        List<Chunk> result = new ArrayList<>();
        for (String text : input) {
            result.addAll(split(text));
        }
        return mergeOverlap(result);
    }

    private List<Chunk> split(String text) {
        return splitRecursive(text, 0);
    }

    private List<Chunk> splitRecursive(String text, int separatorIndex) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (text.length() <= chunkSize) {
            return List.of(new Chunk(text, java.util.Map.of()));
        }
        if (separatorIndex >= separators.size()) {
            return forceSplit(text);
        }

        String sep = separators.get(separatorIndex);
        if (sep.isEmpty()) {
            return forceSplit(text);
        }

        String[] parts = text.split(sep, -1);
        List<Chunk> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String part : parts) {
            String candidate = buffer.isEmpty() ? part : buffer + sep + part;
            if (candidate.length() <= chunkSize) {
                buffer.setLength(0);
                buffer.append(candidate);
            } else {
                if (!buffer.isEmpty()) {
                    result.addAll(splitRecursive(buffer.toString(), separatorIndex + 1));
                    buffer.setLength(0);
                }
                result.addAll(splitRecursive(part, separatorIndex + 1));
            }
        }
        if (!buffer.isEmpty()) {
            result.addAll(splitRecursive(buffer.toString(), separatorIndex + 1));
        }
        return result;
    }

    private List<Chunk> forceSplit(String text) {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(new Chunk(text.substring(i, end), java.util.Map.of()));
        }
        return chunks;
    }

    private List<Chunk> mergeOverlap(List<Chunk> chunks) {
        if (overlap == 0 || chunks.size() <= 1) {
            return chunks;
        }
        List<Chunk> merged = new ArrayList<>();
        merged.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            Chunk prev = merged.getLast();
            Chunk curr = chunks.get(i);
            if (prev.getContent().length() < chunkSize) {
                String combined = prev.getContent() + curr.getContent();
                if (combined.length() <= chunkSize) {
                    merged.set(merged.size() - 1, new Chunk(combined, java.util.Map.of()));
                } else {
                    merged.add(curr);
                }
            } else {
                merged.add(curr);
            }
        }
        return merged;
    }

    public static class Builder {
        private final int chunkSize;
        private int overlap = 0;
        private List<String> separators = DEFAULT_SEPARATORS;
        private InputResolver<List<String>> inputResolver = new DefaultInputResolver.ByKeyResolver<>("texts", List.class, "recursive-chunking");
        private OutputResolver<List<Chunk>> outputResolver = new DefaultOutputResolver<>("chunks");

        private Builder(int chunkSize) {
            if (chunkSize <= 0) {
                throw new IllegalArgumentException("chunkSize must be positive");
            }
            this.chunkSize = chunkSize;
        }

        public Builder overlap(int overlap) {
            if (overlap < 0 || overlap >= chunkSize) {
                throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
            }
            this.overlap = overlap;
            return this;
        }

        public Builder separators(List<String> separators) {
            this.separators = List.copyOf(separators);
            return this;
        }

        public Builder inputResolver(InputResolver<List<String>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<List<Chunk>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public RecursiveCharacterChunkingStep build() {
            return new RecursiveCharacterChunkingStep(chunkSize, overlap, separators, inputResolver, outputResolver);
        }
    }
}