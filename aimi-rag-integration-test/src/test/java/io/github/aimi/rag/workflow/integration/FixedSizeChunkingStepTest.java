package io.github.aimi.rag.workflow.integration;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.step.chunking.FixedSizeChunkingStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FixedSizeChunkingStep")
class FixedSizeChunkingStepTest {

    @Nested
    @DisplayName("process()")
    class ProcessTests {

        @Test
        @DisplayName("TC-CHUNK-FS-001: empty text returns empty list")
        void emptyTextReturnsEmptyList() {
            var step = FixedSizeChunkingStep.builder(500).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of(""));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-CHUNK-FS-002: null text returns empty list")
        void nullTextReturnsEmptyList() {
            var step = FixedSizeChunkingStep.builder(500).overlap(0).build();
            FlowContext context = FlowContext.create();
            List<String> input = new java.util.ArrayList<>();
            input.add(null);
            context.set("texts", input);

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-CHUNK-FS-003: short text under chunkSize — 1 chunk")
        void shortTextUnderChunkSize() {
            var step = FixedSizeChunkingStep.builder(500).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContent()).isEqualTo("hello");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-004: text exactly equals chunkSize — 1 chunk")
        void textExactlyEqualsChunkSize() {
            var step = FixedSizeChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContent()).isEqualTo("hello");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-005: text slightly exceeds chunkSize — 2 chunks")
        void textSlightlyExceedsChunkSize() {
            var step = FixedSizeChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("helloX"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("hello");
            assertThat(result.get(1).getContent()).isEqualTo("X");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-006: text exceeds chunkSize multiple times — 2 full chunks")
        void textExceedsChunkSizeMultipleTimes() {
            var step = FixedSizeChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("abcdefghij"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("abcde");
            assertThat(result.get(1).getContent()).isEqualTo("fghij");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-007: multiple text inputs chunked independently")
        void multipleTextInputsChunkedIndependently() {
            var step = FixedSizeChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello", "world"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("hello");
            assertThat(result.get(1).getContent()).isEqualTo("world");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-008: overlap > 0 — adjacent chunks share characters (4 chunks)")
        void overlapSharesCharacters() {
            var step = FixedSizeChunkingStep.builder(5).overlap(2).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("ABCDE12345"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(4);
            assertThat(result.get(0).getContent()).isEqualTo("ABCDE");
            assertThat(result.get(1).getContent()).isEqualTo("DE123");
            assertThat(result.get(2).getContent()).isEqualTo("2345");
            assertThat(result.get(3).getContent()).isEqualTo("5");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-009: long text with overlap — large scale (4 chunks)")
        void longTextWithOverlapLargeScale() {
            var step = FixedSizeChunkingStep.builder(100).overlap(20).build();
            String text = "A".repeat(250);
            FlowContext context = FlowContext.create();
            context.set("texts", List.of(text));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(4);
            assertThat(result.get(0).getContent()).hasSize(100);
            assertThat(result.get(1).getContent()).hasSize(100);
            assertThat(result.get(2).getContent()).hasSize(90);
            assertThat(result.get(3).getContent()).hasSize(10);
        }
    }

    @Nested
    @DisplayName("property invariants")
    class PropertyTests {

        @Test
        @DisplayName("TC-PROP-001: all chunks content length <= chunkSize")
        void allChunksLengthWithinChunkSize() {
            var step = FixedSizeChunkingStep.builder(5).overlap(0).build();
            String text = "A quick brown fix jumps over the lazy dog";
            FlowContext context = FlowContext.create();
            context.set("texts", List.of(text));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).allMatch(c -> c.getContent().length() <= 5);
        }

        @Test
        @DisplayName("TC-PROP-002: total character count preserved")
        void totalCharacterCountPreserved() {
            var step = FixedSizeChunkingStep.builder(5).overlap(0).build();
            String text = "abcdefghij";
            int expectedTotal = text.length();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of(text));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            int actualTotal = result.stream().mapToInt(c -> c.getContent().length()).sum();
            assertThat(actualTotal).isEqualTo(expectedTotal);
        }
    }

    @Nested
    @DisplayName("Builder error paths")
    class BuilderErrorTests {

        @Test
        @DisplayName("TC-CHUNK-FS-010: Builder chunkSize <= 0 throws")
        void chunkSizeZeroThrows() {
            assertThatThrownBy(() -> FixedSizeChunkingStep.builder(0).overlap(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chunkSize");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-011: Builder overlap < 0 throws")
        void overlapNegativeThrows() {
            assertThatThrownBy(() -> FixedSizeChunkingStep.builder(5).overlap(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("overlap");
        }

        @Test
        @DisplayName("TC-CHUNK-FS-012: Builder overlap >= chunkSize throws")
        void overlapExceedsChunkSizeThrows() {
            assertThatThrownBy(() -> FixedSizeChunkingStep.builder(5).overlap(5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("overlap");
        }
    }
}