package io.github.aimi.rag.workflow.integration;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.step.chunking.RecursiveCharacterChunkingStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecursiveCharacterChunkingStep")
class RecursiveCharacterChunkingStepTest {

    @Nested
    @DisplayName("process()")
    class ProcessTests {

        @Test
        @DisplayName("TC-CHUNK-RC-001: empty text returns empty list")
        void emptyTextReturnsEmptyList() {
            var step = RecursiveCharacterChunkingStep.builder(500).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of(""));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-CHUNK-RC-002: short text under chunkSize — no split")
        void shortTextUnderChunkSize() {
            var step = RecursiveCharacterChunkingStep.builder(500).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContent()).isEqualTo("hello");
        }

        @Test
        @DisplayName("TC-CHUNK-RC-003: split by \\n\\n separator")
        void splitByDoubleNewline() {
            var step = RecursiveCharacterChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("aaa\n\nbbb"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("aaa");
            assertThat(result.get(1).getContent()).isEqualTo("bbb");
        }

        @Test
        @DisplayName("TC-CHUNK-RC-004: fallback to \\n splitting")
        void fallbackToNewlineSplitting() {
            var step = RecursiveCharacterChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("aa bb\ncc dd"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("aa bb");
            assertThat(result.get(1).getContent()).isEqualTo("cc dd");
        }

        @Test
        @DisplayName("TC-CHUNK-RC-005: fallback to space splitting")
        void fallbackToSpaceSplitting() {
            var step = RecursiveCharacterChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello world foo bar"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(4);
            assertThat(result.get(0).getContent()).isEqualTo("hello");
            assertThat(result.get(1).getContent()).isEqualTo("world");
            assertThat(result.get(2).getContent()).isEqualTo("foo");
            assertThat(result.get(3).getContent()).isEqualTo("bar");
        }

        @Test
        @DisplayName("TC-CHUNK-RC-006: final fallback — force split by character")
        void forceSplitByCharacter() {
            var step = RecursiveCharacterChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("abcdefghij"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("abcde");
            assertThat(result.get(1).getContent()).isEqualTo("fghij");
        }

        @Test
        @DisplayName("TC-CHUNK-RC-007: overlap merges short adjacent chunks")
        void overlapMergesShortAdjacentChunks() {
            var step = RecursiveCharacterChunkingStep.builder(10).overlap(5).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello world"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContent()).isEqualTo("helloworld");
        }

        @Test
        @DisplayName("TC-CHUNK-RC-008: custom separators")
        void customSeparators() {
            var step = RecursiveCharacterChunkingStep.builder(5)
                    .separators(List.of(";", ","))
                    .build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("aaa;bbb,ccc;ddd"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(4);
            assertThat(result.get(0).getContent()).isEqualTo("aaa");
            assertThat(result.get(1).getContent()).isEqualTo("bbb");
            assertThat(result.get(2).getContent()).isEqualTo("ccc");
            assertThat(result.get(3).getContent()).isEqualTo("ddd");
        }

        @Test
        @DisplayName("TC-CHUNK-RC-009: multiple text inputs recursive split")
        void multipleTextInputsRecursiveSplit() {
            var step = RecursiveCharacterChunkingStep.builder(5).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("one\n\ntwo", "three"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getContent()).isEqualTo("one");
            assertThat(result.get(1).getContent()).isEqualTo("two");
            assertThat(result.get(2).getContent()).isEqualTo("three");
        }
    }

    @Nested
    @DisplayName("property invariants")
    class PropertyTests {

        @Test
        @DisplayName("TC-PROP-003: all chunks length <= chunkSize")
        void allChunksLengthWithinChunkSize() {
            var step = RecursiveCharacterChunkingStep.builder(10).overlap(0).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello world\nfoo bar\n\nbaz qux longwordhere"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).allMatch(c -> c.getContent().length() <= 10);
        }

        @Test
        @DisplayName("TC-PROP-004: all chunks length <= chunkSize with overlap")
        void allChunksLengthWithinChunkSizeWithOverlap() {
            var step = RecursiveCharacterChunkingStep.builder(10).overlap(5).build();
            FlowContext context = FlowContext.create();
            context.set("texts", List.of("hello world foo bar"));

            step.process(context);

            List<Chunk> result = context.get("chunks", List.class);
            assertThat(result).allMatch(c -> c.getContent().length() <= 10);
        }
    }

    @Nested
    @DisplayName("Builder error paths")
    class BuilderErrorTests {

        @Test
        @DisplayName("TC-CHUNK-RC-010: Builder chunkSize <= 0 throws")
        void chunkSizeZeroThrows() {
            assertThatThrownBy(() -> RecursiveCharacterChunkingStep.builder(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chunkSize");
        }
    }
}