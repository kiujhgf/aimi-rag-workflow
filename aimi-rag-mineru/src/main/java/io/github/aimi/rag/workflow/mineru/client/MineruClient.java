package io.github.aimi.rag.workflow.mineru.client;

import io.github.aimi.rag.workflow.mineru.model.SourceFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * MinerU HTTP 客户端：通过 multipart/form-data 同步调用 {@code POST /file_parse}，
 * 返回原始响应 JSON 字符串。
 *
 * <p>所有解析参数均以 form 字段提交（与 MinerU 3.x FastAPI 契约一致）：
 * {@code backend}、{@code lang_list}、{@code parse_method}、{@code formula_enable}、
 * {@code table_enable}、{@code image_analysis}、{@code start_page_id}、{@code end_page_id}、
 * {@code return_md}（固定 true）。</p>
 */
public class MineruClient {

    private static final String DEFAULT_ENDPOINT = "/file_parse";
    private static final String DEFAULT_BACKEND = "pipeline";
    private static final List<String> DEFAULT_LANG_LIST = List.of("ch");
    private static final int DEFAULT_CONNECT_TIMEOUT = 30;
    private static final int DEFAULT_READ_TIMEOUT = 600;

    private final String baseUrl;
    private final String endpoint;
    private final String backend;
    private final List<String> langList;
    private final String parseMethod;
    private final Boolean formulaEnable;
    private final Boolean tableEnable;
    private final Boolean imageAnalysis;
    private final Integer startPageId;
    private final Integer endPageId;
    private final RestTemplate restTemplate;

    private MineruClient(Builder builder) {
        this.baseUrl = trimTrailingSlash(builder.baseUrl);
        this.endpoint = builder.endpoint != null ? builder.endpoint : DEFAULT_ENDPOINT;
        this.backend = builder.backend != null ? builder.backend : DEFAULT_BACKEND;
        this.langList = builder.langList != null && !builder.langList.isEmpty()
                ? List.copyOf(builder.langList) : DEFAULT_LANG_LIST;
        this.parseMethod = builder.parseMethod;
        this.formulaEnable = builder.formulaEnable;
        this.tableEnable = builder.tableEnable;
        this.imageAnalysis = builder.imageAnalysis;
        this.startPageId = builder.startPageId;
        this.endPageId = builder.endPageId;
        this.restTemplate = builder.restTemplate != null
                ? builder.restTemplate
                : createDefaultRestTemplate(
                        builder.connectTimeout > 0 ? builder.connectTimeout : DEFAULT_CONNECT_TIMEOUT,
                        builder.readTimeout > 0 ? builder.readTimeout : DEFAULT_READ_TIMEOUT);
    }

    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    /**
     * 上传文件列表，同步等待 MinerU 解析完成，返回原始响应 JSON。
     *
     * @param files 要上传解析的文件列表
     * @return MinerU 原始 JSON 响应体
     * @throws MineruClientException HTTP 非 2xx、IO 异常、超时
     */
    public String parse(List<SourceFile> files) throws MineruClientException {
        if (files == null || files.isEmpty()) {
            throw new MineruClientException("files must not be null or empty");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (SourceFile f : files) {
            body.add("files", new NamedByteArrayResource(f));
        }
        body.add("backend", backend);
        for (String lang : langList) {
            body.add("lang_list", lang);
        }
        if (parseMethod != null) {
            body.add("parse_method", parseMethod);
        }
        if (formulaEnable != null) {
            body.add("formula_enable", formulaEnable.toString());
        }
        if (tableEnable != null) {
            body.add("table_enable", tableEnable.toString());
        }
        if (imageAnalysis != null) {
            body.add("image_analysis", imageAnalysis.toString());
        }
        if (startPageId != null) {
            body.add("start_page_id", startPageId.toString());
        }
        if (endPageId != null) {
            body.add("end_page_id", endPageId.toString());
        }
        body.add("return_md", "true");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            return restTemplate.postForObject(baseUrl + endpoint, request, String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new MineruClientException(
                    "MinerU responded with " + e.getStatusCode().value() + ": " + e.getStatusText(), e);
        } catch (RestClientException e) {
            throw new MineruClientException("MinerU request failed: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) throw new IllegalArgumentException("baseUrl must not be null");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static RestTemplate createDefaultRestTemplate(int connectTimeoutSeconds, int readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return new RestTemplate(factory);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getBackend() {
        return backend;
    }

    public List<String> getLangList() {
        return langList;
    }

    public String getParseMethod() {
        return parseMethod;
    }

    /**
     * 带文件名的 ByteArrayResource，multipart 上传时以 {@code files} 为字段名、
     * 源文件名为 part 文件名。
     */
    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        NamedByteArrayResource(SourceFile source) {
            super(source.getContent());
            this.fileName = source.getFileName();
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }

    public static class Builder {
        private final String baseUrl;
        private String endpoint;
        private String backend;
        private List<String> langList;
        private String parseMethod;
        private Boolean formulaEnable;
        private Boolean tableEnable;
        private Boolean imageAnalysis;
        private Integer startPageId;
        private Integer endPageId;
        private int connectTimeout;
        private int readTimeout;
        private RestTemplate restTemplate;

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder backend(String backend) {
            this.backend = backend;
            return this;
        }

        public Builder langList(List<String> langList) {
            this.langList = langList;
            return this;
        }

        public Builder parseMethod(String parseMethod) {
            this.parseMethod = parseMethod;
            return this;
        }

        public Builder formulaEnable(boolean formulaEnable) {
            this.formulaEnable = formulaEnable;
            return this;
        }

        public Builder tableEnable(boolean tableEnable) {
            this.tableEnable = tableEnable;
            return this;
        }

        public Builder imageAnalysis(boolean imageAnalysis) {
            this.imageAnalysis = imageAnalysis;
            return this;
        }

        public Builder startPageId(Integer startPageId) {
            this.startPageId = startPageId;
            return this;
        }

        public Builder endPageId(Integer endPageId) {
            this.endPageId = endPageId;
            return this;
        }

        public Builder connectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder restTemplate(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
            return this;
        }

        public MineruClient build() {
            return new MineruClient(this);
        }
    }
}
