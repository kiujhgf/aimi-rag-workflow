package io.github.aimi.rag.workflow.mineru.resolver;

import io.github.aimi.rag.workflow.mineru.model.SourceFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownResponseParserTest {

    private final MarkdownResponseParser parser = new MarkdownResponseParser();

    @Nested
    @DisplayName("正常解析")
    class HappyPath {

        @Test
        @DisplayName("results.<去扩展名文件名>.md_content 按输入顺序输出")
        void shouldExtractMdContentInSourceOrder() {
            String body = """
                    {
                      "task_id": "t1",
                      "status": "completed",
                      "error": null,
                      "results": {
                        "a": {"md_content": "# A"},
                        "b": {"md_content": "# B"}
                      }
                    }
                    """;

            List<String> texts = parser.parse(body, List.of(
                    new SourceFile("b.pdf", new byte[]{2}),
                    new SourceFile("a.pdf", new byte[]{1})));

            assertEquals(List.of("# B", "# A"), texts);
        }

        @Test
        @DisplayName("results 的 key 与完整文件名一致时也能匹配")
        void shouldMatchFullFileNameKey() {
            String body = """
                    {"status":"completed","results":{"a.pdf":{"md_content":"full-name"}}}
                    """;

            List<String> texts = parser.parse(body, List.of(new SourceFile("a.pdf", new byte[]{1})));
            assertEquals(List.of("full-name"), texts);
        }

        @Test
        @DisplayName("md_content 为嵌套对象时取内层文本")
        void shouldHandleNestedMdContent() {
            String body = """
                    {"status":"completed","results":{"a":{"md_content":{"markdown":"nested"}}}}
                    """;

            List<String> texts = parser.parse(body, List.of(new SourceFile("a.pdf", new byte[]{1})));
            assertEquals(List.of("nested"), texts);
        }

        @Test
        @DisplayName("sources 为空时按响应顺序输出全部结果")
        void shouldReturnAllResultsWhenNoSources() {
            String body = """
                    {"status":"completed","results":{"a":{"md_content":"x"},"b":{"md_content":"y"}}}
                    """;

            assertEquals(2, parser.parse(body, null).size());
            assertEquals(2, parser.parse(body, List.of()).size());
        }

        @Test
        @DisplayName("源文件在 results 中不存在时跳过该文件")
        void shouldSkipMissingSources() {
            String body = """
                    {"status":"completed","results":{"a":{"md_content":"only-a"}}}
                    """;

            List<String> texts = parser.parse(body, List.of(
                    new SourceFile("a.pdf", new byte[]{1}),
                    new SourceFile("missing.pdf", new byte[]{2})));

            assertEquals(List.of("only-a"), texts);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("空响应体抛 MineruParseException")
        void shouldRejectEmptyBody() {
            assertThrows(MineruParseException.class, () -> parser.parse("", null));
        }

        @Test
        @DisplayName("无效 JSON 抛 MineruParseException")
        void shouldRejectInvalidJson() {
            MineruParseException ex = assertThrows(MineruParseException.class, () -> parser.parse("not-json", null));
            assertTrue(ex.getMessage().contains("Failed to parse"));
        }

        @Test
        @DisplayName("缺少 results 抛 MineruParseException")
        void shouldRejectMissingResults() {
            MineruParseException ex = assertThrows(MineruParseException.class, () -> parser.parse("{\"status\":\"completed\"}", null));
            assertTrue(ex.getMessage().contains("missing 'results'"));
        }

        @Test
        @DisplayName("error 非空抛 MineruParseException")
        void shouldThrowWhenErrorPresent() {
            MineruParseException ex = assertThrows(MineruParseException.class,
                    () -> parser.parse("{\"status\":\"failed\",\"error\":\"backend crashed\"}", null));
            assertTrue(ex.getMessage().contains("backend crashed"));
        }

        @Test
        @DisplayName("status 非 completed 抛 MineruParseException")
        void shouldThrowWhenStatusNotCompleted() {
            MineruParseException ex = assertThrows(MineruParseException.class,
                    () -> parser.parse("{\"status\":\"pending\",\"results\":{}}", null));
            assertTrue(ex.getMessage().contains("pending"));
        }
    }
}
