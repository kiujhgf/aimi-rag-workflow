package io.github.aimi.rag.workflow.integration;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowDecider;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.flow.Flow;
import io.github.aimi.rag.workflow.core.flow.FlowBuilder;
import io.github.aimi.rag.workflow.ingest.model.*;
import io.github.aimi.rag.workflow.es.converter.DefaultStorageItemConverter;
import io.github.aimi.rag.workflow.ingest.step.chunking.FixedSizeChunkingStep;
import io.github.aimi.rag.workflow.ingest.step.chunking.RecursiveCharacterChunkingStep;
import io.github.aimi.rag.workflow.ingest.step.embedding.IngestEmbeddingStep;
import io.github.aimi.rag.workflow.ingest.step.input.StringInputStep;
import io.github.aimi.rag.workflow.ingest.step.storage.StorageStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Converter & Flow")
class ConverterAndFlowTest {

    @Nested
    @DisplayName("DefaultStorageItemConverter.toDoc()")
    class ConverterTests {

        @Test
        @DisplayName("TC-CONV-001: Chunk toDoc — no _vector")
        void chunkToDocNoVector() {
            var converter = new DefaultStorageItemConverter();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("key", "val");
            Chunk chunk = new Chunk("test text", metadata);

            Map<String, Object> doc = converter.toDoc(chunk, FlowContext.create());

            assertThat(doc).containsEntry("content", "test text");
            assertThat(doc).containsEntry("key", "val");
            assertThat(doc).doesNotContainKey("_vector");
        }

        @Test
        @DisplayName("TC-CONV-002: Embedding toDoc — includes _vector")
        void embeddingToDocIncludesVector() {
            var converter = new DefaultStorageItemConverter();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("src", "doc1");
            Chunk sourceChunk = new Chunk("embed text", metadata);
            float[] vector = {1.0f, 2.0f, 3.0f};
            Embedding embedding = new Embedding(sourceChunk, vector);

            Map<String, Object> doc = converter.toDoc(embedding, FlowContext.create());

            assertThat(doc).containsEntry("content", "embed text");
            assertThat(doc).containsEntry("src", "doc1");
            assertThat(doc).containsKey("_vector");
            assertThat(doc.get("_vector")).isInstanceOf(List.class);
            assertThat((List<Float>) doc.get("_vector")).containsExactly(1.0f, 2.0f, 3.0f);
        }

        @Test
        @DisplayName("TC-CONV-003: Chunk empty metadata — content-only")
        void chunkEmptyMetadataContentOnly() {
            var converter = new DefaultStorageItemConverter();
            Chunk chunk = new Chunk("plain text", Map.of());

            Map<String, Object> doc = converter.toDoc(chunk, FlowContext.create());

            assertThat(doc).containsEntry("content", "plain text");
            assertThat(doc).hasSize(1);
        }

        @Test
        @DisplayName("TC-PROP-005: toDoc always contains content key")
        void toDocAlwaysContainsContent() {
            var converter = new DefaultStorageItemConverter();
            Chunk chunk = new Chunk("any text", Map.of());

            Map<String, Object> doc = converter.toDoc(chunk, FlowContext.create());

            assertThat(doc).containsKey("content");
        }
    }

    @Nested
    @DisplayName("FlowBuilder.build()")
    class FlowBuilderTests {

        static StorageStep<Chunk> textStorage() {
            return new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "text-storage")) {
                @Override
                public String getName() { return "text-storage"; }

                @Override
                protected StorageResult doProcess(List<Chunk> input, FlowContext context) {
                    return new StorageResult(input.size());
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };
        }

        static StorageStep<Embedding> vectorStorage() {
            return new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("embeddings", List.class, "vector-storage")) {
                @Override
                public String getName() { return "vector-storage"; }

                @Override
                protected StorageResult doProcess(List<Embedding> input, FlowContext context) {
                    return new StorageResult(input.size());
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };
        }

        static IngestEmbeddingStep embedding() {
            return new IngestEmbeddingStep(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "test-embedding")) {
                @Override
                public String getName() { return "test-embedding"; }

                @Override
                protected List<Embedding> doProcess(List<Chunk> input, FlowContext context) {
                    List<Embedding> result = new ArrayList<>();
                    for (Chunk c : input) {
                        result.add(new Embedding(c, new float[]{1.0f}));
                    }
                    return result;
                }
            };
        }

        @Test
        @DisplayName("TC-FLOW-001: Flow text path builds")
        void textPathBuilds() {
            Flow flow = FlowBuilder.create()
                    .step(StringInputStep.builder().build())
                    .step(FixedSizeChunkingStep.builder(500).overlap(0).build())
                    .step(textStorage())
                    .build();

            assertThat(flow).isNotNull();
        }

        @Test
        @DisplayName("TC-FLOW-002: Flow vector path builds")
        void vectorPathBuilds() {
            Flow flow = FlowBuilder.create()
                    .step(StringInputStep.builder().build())
                    .step(RecursiveCharacterChunkingStep.builder(500).overlap(0).build())
                    .step(embedding())
                    .step(vectorStorage())
                    .build();

            assertThat(flow).isNotNull();
        }

        @Test
        @DisplayName("TC-FLOW-003: text path execute returns IngestResult with success=true")
        void textPathExecuteReturnsIngestResult() {
            Flow flow = FlowBuilder.create()
                    .step(StringInputStep.builder().build())
                    .step(FixedSizeChunkingStep.builder(500).overlap(0).build())
                    .step(textStorage())
                    .build();

            FlowContext context = FlowContext.create();
            context.set("input", List.of("hello world"));
            context = flow.execute(context);

            StorageResult storageResult = context.get("storage_result", StorageResult.class);
            IngestResult result = new IngestResult(storageResult != null ? storageResult.getStoredCount() : 0, true);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStoredCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-FLOW-004: conditional branch with FlowDecider - TRUE path")
        void conditionalBranchWithDeciderTruePath() {
            FlowDecider decider = ctx -> {
                List<String> input = ctx.get("input", List.class);
                return input.size() > 0 ? "HAS_DATA" : "NO_DATA";
            };

            StorageStep<Chunk> trueStorage = new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "true-storage")) {
                @Override
                public String getName() { return "true-storage"; }

                @Override
                protected StorageResult doProcess(List<Chunk> input, FlowContext context) {
                    return new StorageResult(input.size());
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };

            StorageStep<Chunk> falseStorage = new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "false-storage")) {
                @Override
                public String getName() { return "false-storage"; }

                @Override
                protected StorageResult doProcess(List<Chunk> input, FlowContext context) {
                    return new StorageResult(0);
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };

            Flow flow = FlowBuilder.create()
                    .step(StringInputStep.builder().build())
                    .step(FixedSizeChunkingStep.builder(500).overlap(0).build())
                    .decider(decider, "data-checker")
                    .on("HAS_DATA").to(trueStorage)
                    .on("NO_DATA").to(falseStorage)
                    .build();

            FlowContext context = FlowContext.create();
            context.set("input", List.of("hello world"));
            context = flow.execute(context);

            StorageResult storageResult = context.get("storage_result", StorageResult.class);
            IngestResult result = new IngestResult(storageResult != null ? storageResult.getStoredCount() : 0, true);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStoredCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-FLOW-005: conditional branch with FlowDecider - FALSE path")
        void conditionalBranchWithDeciderFalsePath() {
            FlowDecider decider = ctx -> {
                List<String> input = ctx.get("input", List.class);
                return input.size() > 0 ? "HAS_DATA" : "NO_DATA";
            };

            StorageStep<Chunk> trueStorage = new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "true-storage")) {
                @Override
                public String getName() { return "true-storage"; }

                @Override
                protected StorageResult doProcess(List<Chunk> input, FlowContext context) {
                    return new StorageResult(input.size());
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };

            StorageStep<Chunk> falseStorage = new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "false-storage")) {
                @Override
                public String getName() { return "false-storage"; }

                @Override
                protected StorageResult doProcess(List<Chunk> input, FlowContext context) {
                    return new StorageResult(0);
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };

            Flow flow = FlowBuilder.create()
                    .step(StringInputStep.builder().build())
                    .step(FixedSizeChunkingStep.builder(500).overlap(0).build())
                    .decider(decider, "data-checker")
                    .on("HAS_DATA").to(trueStorage)
                    .on("NO_DATA").to(falseStorage)
                    .build();

            FlowContext context = FlowContext.create();
            context.set("input", List.of());
            context = flow.execute(context);

            StorageResult storageResult = context.get("storage_result", StorageResult.class);
            IngestResult result = new IngestResult(storageResult != null ? storageResult.getStoredCount() : 0, true);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStoredCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("TC-FLOW-006: repeat loop with loop()/endLoop() - executes 3 times")
        void repeatLoopWithDeciderExecutes3Times() {
            StorageStep<Chunk> countingStep = new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "counting-step")) {
                @Override
                public String getName() { return "counting-step"; }

                @Override
                protected StorageResult doProcess(List<Chunk> input, FlowContext context) {
                    Integer count = context.get("loop_count", 0);
                    context.set("loop_count", count + 1);
                    return new StorageResult(input.size());
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };

            FlowDecider loopDecider = ctx -> {
                Integer count = ctx.get("loop_count", 0);
                return count < 3 ? "CONTINUE" : "EXIT";
            };

            StorageStep<Chunk> finalStorage = new StorageStep<>(new DefaultInputResolver.ByKeyResolver<>("chunks", List.class, "final-storage")) {
                @Override
                public String getName() { return "final-storage"; }

                @Override
                protected StorageResult doProcess(List<Chunk> input, FlowContext context) {
                    return new StorageResult(99);
                }

                @Override
                protected void writeOutput(StorageResult output, FlowContext context) {
                    context.set("storage_result", output);
                }
            };

            Flow flow = FlowBuilder.create()
                    .step(StringInputStep.builder().build())
                    .step(FixedSizeChunkingStep.builder(500).overlap(0).build())
                    .loop(loopDecider)
                    .step(countingStep)
                    .endLoop()
                    .step(finalStorage)
                    .build();

            FlowContext context = FlowContext.create();
            context.set("input", List.of("test"));
            context = flow.execute(context);

            StorageResult storageResult = context.get("storage_result", StorageResult.class);
            IngestResult result = new IngestResult(storageResult != null ? storageResult.getStoredCount() : 0, true);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStoredCount()).isEqualTo(99);
        }
    }
}