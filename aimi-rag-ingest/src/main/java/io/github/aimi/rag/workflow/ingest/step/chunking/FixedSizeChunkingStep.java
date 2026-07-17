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

public class FixedSizeChunkingStep extends ChunkingStep {

    private final int chunkSize;
    private final int overlap;

    private FixedSizeChunkingStep(int chunkSize, int overlap, InputResolver<List<String>> inputResolver, OutputResolver<List<Chunk>> outputResolver) {
        super(inputResolver, outputResolver);
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public static Builder builder(int chunkSize) {
        return new Builder(chunkSize);
    }

    @Override
    public String getName() {
        return "fixed-size-chunking";
    }

    @Override
    protected List<Chunk> doProcess(List<String> input, FlowContext context) throws StepExecuteException {
        List<Chunk> chunks = new ArrayList<>();
        for (String text : input) {
            chunks.addAll(chunk(text));
        }
        return chunks;
    }

    private List<Chunk> chunk(String text) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int step = chunkSize - overlap;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(new Chunk(text.substring(start, end), java.util.Map.of()));
            start += step;
        }
        return chunks;
    }

    public static class Builder {
        private final int chunkSize;
        private int overlap = 0;
        private InputResolver<List<String>> inputResolver = new DefaultInputResolver.ByKeyResolver<>("texts", List.class, "fixed-size-chunking");
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

        public Builder inputResolver(InputResolver<List<String>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<List<Chunk>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public FixedSizeChunkingStep build() {
            return new FixedSizeChunkingStep(chunkSize, overlap, inputResolver, outputResolver);
        }
    }
}