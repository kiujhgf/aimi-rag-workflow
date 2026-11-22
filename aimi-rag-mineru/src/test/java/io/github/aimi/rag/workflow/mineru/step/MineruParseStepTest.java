package io.github.aimi.rag.workflow.mineru.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.mineru.client.MineruClient;
import io.github.aimi.rag.workflow.mineru.client.MineruClientException;
import io.github.aimi.rag.workflow.mineru.model.SourceFile;
import io.github.aimi.rag.workflow.mineru.resolver.MineruResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class MineruParseStepTest {

    private MineruClient client;
    private MineruResponseParser parser;

    @BeforeEach
    void setUp() {
        client = mock(MineruClient.class);
        parser = mock(MineruResponseParser.class);
    }

    @Nested
    @DisplayName("元信息")
    class Metadata {

        @Test
        @DisplayName("name = mineru-parse, type = PARSING")
        void shouldExposeNameAndType() {
            MineruParseStep step = MineruParseStep.builder(client).build();
            assertEquals("mineru-parse", step.getName());
            assertEquals(StepType.PARSING, step.getType());
        }

        @Test
        @DisplayName("默认 category = INGEST，可覆盖")
        void shouldDefaultToIngestAndAllowOverride() {
            assertEquals(FlowCategory.INGEST, MineruParseStep.builder(client).build().getCategory());

            MineruParseStep queryStep = MineruParseStep.builder(client)
                    .category(FlowCategory.QUERY)
                    .build();
            assertEquals(FlowCategory.QUERY, queryStep.getCategory());
        }
    }

    @Nested
    @DisplayName("成功流程")
    class Success {

        @Test
        @DisplayName("解析结果写入 FlowContext 默认 key texts")
        void shouldWriteTextsToDefaultKey() throws Exception {
            SourceFile file = new SourceFile("doc.pdf", "content".getBytes());
            FlowContext ctx = FlowContext.create().set("sourceFiles", List.of(file));

            when(client.parse(List.of(file))).thenReturn("{\"status\":\"completed\",\"results\":{}}");
            when(parser.parse("{\"status\":\"completed\",\"results\":{}}", List.of(file)))
                    .thenReturn(List.of("# Title", "body"));

            MineruParseStep step = MineruParseStep.builder(client).responseParser(parser).build();
            StepStatus status = step.process(ctx);

            assertEquals(StepStatus.FINISHED, status);
            assertEquals(List.of("# Title", "body"), ctx.get("texts"));

            verify(client).parse(List.of(file));
            verify(parser).parse("{\"status\":\"completed\",\"results\":{}}", List.of(file));
        }

        @Test
        @DisplayName("自定义输出 key 生效")
        void shouldWriteToCustomOutputKey() throws Exception {
            SourceFile file = new SourceFile("doc.pdf", "content".getBytes());
            FlowContext ctx = FlowContext.create().set("sourceFiles", List.of(file));

            when(client.parse(anyList())).thenReturn("{}");
            when(parser.parse(any(), anyList())).thenReturn(List.of("x"));

            MineruParseStep step = MineruParseStep.builder(client)
                    .responseParser(parser)
                    .outputResolver(new DefaultOutputResolver<>("myTexts"))
                    .build();
            step.process(ctx);

            assertEquals(List.of("x"), ctx.get("myTexts"));
        }
    }

    @Nested
    @DisplayName("异常流程")
    class Error {

        @Test
        @DisplayName("MineruClientException 导致 Step 抛 StepExecuteException")
        void shouldFailOnClientException() throws Exception {
            SourceFile file = new SourceFile("doc.pdf", "content".getBytes());
            FlowContext ctx = FlowContext.create().set("sourceFiles", List.of(file));

            when(client.parse(anyList())).thenThrow(new MineruClientException("500 boom"));

            MineruParseStep step = MineruParseStep.builder(client).responseParser(parser).build();

            assertThrows(StepExecuteException.class, () -> step.process(ctx));
            assertEquals(StepStatus.FAILED, step.getStatus());
        }
    }

    @Nested
    @DisplayName("ConfigurableBuilder")
    class Configurable {

        @Test
        @DisplayName("内部构造 Client 并传递配置，默认 backend=pipeline")
        void shouldBuildClientInternally() {
            MineruParseStep step = MineruParseStep.builder()
                    .baseUrl("http://localhost:8000")
                    .langList(List.of("ch", "en"))
                    .parseMethod("auto")
                    .build();

            MineruClient builtClient = step.getClient();
            assertEquals("http://localhost:8000", builtClient.getBaseUrl());
            assertEquals("/file_parse", builtClient.getEndpoint());
            assertEquals("pipeline", builtClient.getBackend());
            assertEquals(List.of("ch", "en"), builtClient.getLangList());
            assertEquals("auto", builtClient.getParseMethod());
        }

        @Test
        @DisplayName("自定义 backend 覆盖默认值")
        void shouldOverrideBackend() {
            MineruParseStep step = MineruParseStep.builder()
                    .baseUrl("http://host:8000")
                    .backend("vlm-auto-engine")
                    .build();

            assertEquals("vlm-auto-engine", step.getClient().getBackend());
        }

        @Test
        @DisplayName("baseUrl 缺失抛异常")
        void shouldRejectMissingBaseUrl() {
            assertThrows(IllegalArgumentException.class, () -> MineruParseStep.builder().build());
        }
    }
}
