package io.github.aimi.rag.workflow.mineru.client;

import io.github.aimi.rag.workflow.mineru.model.SourceFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * MineruClient 单元测试：
 * - 请求路径 /file_parse，参数以 multipart form 字段提交
 * - 非 2xx 响应抛 MineruClientException
 */
class MineruClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MineruClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = MineruClient.builder("http://localhost:8000")
                .restTemplate(restTemplate)
                .build();
    }

    @Nested
    @DisplayName("请求构造")
    class RequestConstruction {

        @Test
        @DisplayName("路径 /file_parse，form 字段与默认值正确")
        void shouldBuildMultipartFormRequest() {
            String fakeResponse = "{\"status\":\"completed\",\"results\":{}}";
            server.expect(once(), request -> {
                assertEquals("/file_parse", request.getURI().getPath());
                assertNull(request.getURI().getQuery());
                assertEquals(HttpMethod.POST, request.getMethod());

                MediaType contentType = request.getHeaders().getContentType();
                assertNotNull(contentType);
                assertEquals("multipart", contentType.getType());
                assertEquals("form-data", contentType.getSubtype());
                assertNotNull(contentType.getParameter("boundary"));

                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertTrue(body.contains("name=\"files\""));
                assertTrue(body.contains("filename=\"test.pdf\""));
                assertTrue(body.contains("name=\"backend\""));
                assertTrue(body.contains("pipeline"));
                assertTrue(body.contains("name=\"lang_list\""));
                assertTrue(body.contains("\"ch\"") || body.contains("ch\r\n"));
                assertTrue(body.contains("name=\"return_md\""));
            }).andRespond(withSuccess(fakeResponse, MediaType.APPLICATION_JSON));

            String result = client.parse(List.of(new SourceFile("test.pdf", "fake-content".getBytes())));

            assertEquals(fakeResponse, result);
            server.verify();
        }

        @Test
        @DisplayName("自定义 backend / langList / parseMethod / 页范围覆盖默认")
        void shouldSubmitCustomConfigValues() {
            server.expect(once(), request -> {
                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertTrue(body.contains("vlm-auto-engine"));
                assertTrue(body.contains("name=\"parse_method\""));
                assertTrue(body.contains("ocr"));
                assertTrue(body.contains("name=\"start_page_id\""));
                assertTrue(body.contains("name=\"end_page_id\""));
                assertTrue(body.contains("999"));
            }).andRespond(withSuccess("{\"status\":\"completed\",\"results\":{}}", MediaType.APPLICATION_JSON));

            MineruClient customClient = MineruClient.builder("http://localhost:8000")
                    .restTemplate(restTemplate)
                    .backend("vlm-auto-engine")
                    .langList(List.of("ch", "en"))
                    .parseMethod("ocr")
                    .startPageId(0)
                    .endPageId(999)
                    .build();

            customClient.parse(List.of(new SourceFile("a.pdf", new byte[]{1})));
            server.verify();
        }

        @Test
        @DisplayName("多个文件生成多个 files part")
        void shouldSendMultipleFileParts() {
            server.expect(once(), request -> {
                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertTrue(body.contains("filename=\"a.pdf\""));
                assertTrue(body.contains("filename=\"b.pdf\""));
            }).andRespond(withSuccess("{\"status\":\"completed\",\"results\":{}}", MediaType.APPLICATION_JSON));

            client.parse(List.of(
                    new SourceFile("a.pdf", new byte[]{1}),
                    new SourceFile("b.pdf", new byte[]{2})));
            server.verify();
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("4xx 响应抛 MineruClientException")
        void shouldThrowOn4xx() {
            server.expect(once(), request -> assertEquals("/file_parse", request.getURI().getPath()))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            MineruClientException ex = assertThrows(MineruClientException.class,
                    () -> client.parse(List.of(new SourceFile("a.pdf", new byte[]{1}))));
            assertTrue(ex.getMessage().contains("400"));
            server.verify();
        }

        @Test
        @DisplayName("5xx 响应抛 MineruClientException")
        void shouldThrowOn5xx() {
            server.expect(once(), request -> assertEquals("/file_parse", request.getURI().getPath()))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            MineruClientException ex = assertThrows(MineruClientException.class,
                    () -> client.parse(List.of(new SourceFile("a.pdf", new byte[]{1}))));
            assertTrue(ex.getMessage().contains("500"));
            server.verify();
        }

        @Test
        @DisplayName("空文件列表直接抛异常")
        void shouldRejectEmptyFileList() {
            MineruClientException ex = assertThrows(MineruClientException.class, () -> client.parse(List.of()));
            assertTrue(ex.getMessage().contains("not be null or empty"));
        }
    }

    @Nested
    @DisplayName("配置校验")
    class Validation {

        @Test
        @DisplayName("baseUrl 为 null 抛异常")
        void shouldRejectNullBaseUrl() {
            assertThrows(IllegalArgumentException.class, () -> MineruClient.builder(null).build());
        }

        @Test
        @DisplayName("baseUrl 尾部斜杠自动裁剪")
        void shouldTrimTrailingSlash() {
            MineruClient c = MineruClient.builder("http://host:9000/").build();
            assertEquals("http://host:9000", c.getBaseUrl());
            assertEquals("/file_parse", c.getEndpoint());
            assertEquals("pipeline", c.getBackend());
        }
    }
}
