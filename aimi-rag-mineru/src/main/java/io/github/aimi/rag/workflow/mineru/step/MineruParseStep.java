package io.github.aimi.rag.workflow.mineru.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.mineru.client.MineruClient;
import io.github.aimi.rag.workflow.mineru.client.MineruClientException;
import io.github.aimi.rag.workflow.mineru.model.SourceFile;
import io.github.aimi.rag.workflow.mineru.resolver.MarkdownResponseParser;
import io.github.aimi.rag.workflow.mineru.resolver.MineruParseException;
import io.github.aimi.rag.workflow.mineru.resolver.MineruResponseParser;

import java.util.List;

/**
 * MinerU 文档解析 Step：上传 {@link SourceFile} 列表，调用 MinerU 同步接口，
 * 产出解析后的纯文本 {@code List<String>}。
 *
 * <p>不绑定 ingest 流程，可通过 {@link Builder#category(FlowCategory)} 复用于 query 等任意流程。</p>
 */
public class MineruParseStep extends AbstractStep<List<SourceFile>, List<String>> {

    public static final String STEP_NAME = "mineru-parse";
    public static final String DEFAULT_INPUT_KEY = "sourceFiles";
    public static final String DEFAULT_OUTPUT_KEY = "texts";

    private final MineruClient client;
    private final MineruResponseParser responseParser;
    private final FlowCategory category;

    private MineruParseStep(MineruClient client,
                            MineruResponseParser responseParser,
                            FlowCategory category,
                            InputResolver<List<SourceFile>> inputResolver,
                            OutputResolver<List<String>> outputResolver) {
        super(inputResolver, outputResolver);
        this.client = client;
        this.responseParser = responseParser;
        this.category = category != null ? category : FlowCategory.INGEST;
    }

    /**
     * Builder：调用方自行构造 MineruClient 传入（已配置好 baseUrl/backend 等）。
     */
    public static Builder builder(MineruClient client) {
        return new Builder(client);
    }

    /**
     * ConfigurableBuilder：只传 baseUrl，内部自己构造 MineruClient。
     */
    public static ConfigurableBuilder builder() {
        return new ConfigurableBuilder();
    }

    @Override
    public String getName() {
        return STEP_NAME;
    }

    @Override
    public StepType getType() {
        return StepType.PARSING;
    }

    @Override
    public FlowCategory getCategory() {
        return category;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<SourceFile>> getInputType() {
        return (Class<List<SourceFile>>) (Class<?>) List.class;
    }

    @Override
    protected List<String> doProcess(List<SourceFile> input, FlowContext context) throws StepExecuteException {
        try {
            String responseBody = client.parse(input);
            return responseParser.parse(responseBody, input);
        } catch (MineruClientException | MineruParseException e) {
            throw new StepExecuteException(STEP_NAME, e);
        }
    }

    public MineruClient getClient() {
        return client;
    }

    public MineruResponseParser getResponseParser() {
        return responseParser;
    }

    // ---- Builder：调用方传入 MineruClient ----

    public static class Builder {
        private final MineruClient client;
        private MineruResponseParser responseParser = new MarkdownResponseParser();
        private FlowCategory category;
        private InputResolver<List<SourceFile>> inputResolver = new DefaultInputResolver.ByKeyResolver<>(
                DEFAULT_INPUT_KEY, List.class, STEP_NAME);
        private OutputResolver<List<String>> outputResolver = new DefaultOutputResolver<>(DEFAULT_OUTPUT_KEY);

        private Builder(MineruClient client) {
            if (client == null) throw new IllegalArgumentException("MineruClient must not be null");
            this.client = client;
        }

        public Builder responseParser(MineruResponseParser responseParser) {
            this.responseParser = responseParser;
            return this;
        }

        public Builder category(FlowCategory category) {
            this.category = category;
            return this;
        }

        public Builder inputResolver(InputResolver<List<SourceFile>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<List<String>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public MineruParseStep build() {
            return new MineruParseStep(client, responseParser, category, inputResolver, outputResolver);
        }
    }

    // ---- ConfigurableBuilder：只传 baseUrl，内部构造 MineruClient ----

    public static class ConfigurableBuilder {
        private String baseUrl;
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
        private MineruResponseParser responseParser;
        private FlowCategory category;
        private InputResolver<List<SourceFile>> inputResolver;
        private OutputResolver<List<String>> outputResolver;

        private ConfigurableBuilder() {}

        public ConfigurableBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ConfigurableBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public ConfigurableBuilder backend(String backend) {
            this.backend = backend;
            return this;
        }

        public ConfigurableBuilder langList(List<String> langList) {
            this.langList = langList;
            return this;
        }

        public ConfigurableBuilder parseMethod(String parseMethod) {
            this.parseMethod = parseMethod;
            return this;
        }

        public ConfigurableBuilder formulaEnable(boolean formulaEnable) {
            this.formulaEnable = formulaEnable;
            return this;
        }

        public ConfigurableBuilder tableEnable(boolean tableEnable) {
            this.tableEnable = tableEnable;
            return this;
        }

        public ConfigurableBuilder imageAnalysis(boolean imageAnalysis) {
            this.imageAnalysis = imageAnalysis;
            return this;
        }

        public ConfigurableBuilder startPageId(Integer startPageId) {
            this.startPageId = startPageId;
            return this;
        }

        public ConfigurableBuilder endPageId(Integer endPageId) {
            this.endPageId = endPageId;
            return this;
        }

        public ConfigurableBuilder connectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ConfigurableBuilder readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public ConfigurableBuilder responseParser(MineruResponseParser responseParser) {
            this.responseParser = responseParser;
            return this;
        }

        public ConfigurableBuilder category(FlowCategory category) {
            this.category = category;
            return this;
        }

        public ConfigurableBuilder inputResolver(InputResolver<List<SourceFile>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder outputResolver(OutputResolver<List<String>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public MineruParseStep build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be blank");
            }

            MineruClient.Builder clientBuilder = MineruClient.builder(baseUrl);
            if (endpoint != null) clientBuilder.endpoint(endpoint);
            if (backend != null) clientBuilder.backend(backend);
            if (langList != null) clientBuilder.langList(langList);
            if (parseMethod != null) clientBuilder.parseMethod(parseMethod);
            if (formulaEnable != null) clientBuilder.formulaEnable(formulaEnable);
            if (tableEnable != null) clientBuilder.tableEnable(tableEnable);
            if (imageAnalysis != null) clientBuilder.imageAnalysis(imageAnalysis);
            if (startPageId != null) clientBuilder.startPageId(startPageId);
            if (endPageId != null) clientBuilder.endPageId(endPageId);
            if (connectTimeout > 0) clientBuilder.connectTimeout(connectTimeout);
            if (readTimeout > 0) clientBuilder.readTimeout(readTimeout);

            MineruClient client = clientBuilder.build();
            MineruResponseParser parser = responseParser != null ? responseParser : new MarkdownResponseParser();
            InputResolver<List<SourceFile>> input = inputResolver != null ? inputResolver
                    : new DefaultInputResolver.ByKeyResolver<>(DEFAULT_INPUT_KEY, List.class, STEP_NAME);
            OutputResolver<List<String>> output = outputResolver != null ? outputResolver
                    : new DefaultOutputResolver<>(DEFAULT_OUTPUT_KEY);

            return new MineruParseStep(client, parser, category, input, output);
        }
    }
}
