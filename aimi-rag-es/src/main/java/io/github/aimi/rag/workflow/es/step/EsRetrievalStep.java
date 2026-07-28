package io.github.aimi.rag.workflow.es.step;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.es.client.EsClient;
import io.github.aimi.rag.workflow.query.model.RetrievalResult;
import io.github.aimi.rag.workflow.query.model.RetrievalType;
import io.github.aimi.rag.workflow.query.model.SearchResult;
import io.github.aimi.rag.workflow.query.step.retrieval.RetrievalStep;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EsRetrievalStep extends RetrievalStep {

    private final EsClient esClient;
    private final String indexName;
    private final String vectorField;
    private final String contentField;
    private final String keywordField;
    private final RetrievalType retrievalType;
    private final int topK;

    private EsRetrievalStep(EsClient esClient, String indexName, String vectorField,
                           String contentField, String keywordField, RetrievalType retrievalType,
                           int topK, InputResolver<float[]> inputResolver,
                           OutputResolver<RetrievalResult> outputResolver) {
        super(inputResolver, outputResolver);
        this.esClient = esClient;
        this.indexName = indexName;
        this.vectorField = vectorField;
        this.contentField = contentField;
        this.keywordField = keywordField;
        this.retrievalType = retrievalType;
        this.topK = topK;
    }

    public static Builder builder(EsClient esClient) {
        return new Builder(esClient);
    }

    public static ConfigurableBuilder builder() {
        return new ConfigurableBuilder();
    }

    @Override
    public String getName() {
        return "es-retrieval";
    }

    @Override
    protected RetrievalResult doProcess(float[] queryVector, FlowContext context) throws StepExecuteException {
        String queryText = context.get("query_text", String.class);
        try {
            SearchResponse<Map> response;
            switch (retrievalType) {
                case VECTOR:
                    response = esClient.searchByVector(indexName, vectorField, queryVector, topK);
                    break;
                case KEYWORD:
                    response = esClient.searchByKeyword(indexName, keywordField, queryText, topK);
                    break;
                case HYBRID:
                    response = esClient.searchHybrid(indexName, vectorField, queryVector, keywordField, queryText, topK);
                    break;
                default:
                    throw new StepExecuteException(getName(), new IllegalArgumentException("Unknown retrieval type: " + retrievalType));
            }

            List<SearchResult> results = response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> source = hit.source();
                        String content = (String) source.get(contentField);
                        float score = hit.score() != null ? hit.score().floatValue() : 0.0f;
                        String documentId = hit.id();
                        return new SearchResult(content, score, documentId);
                    })
                    .collect(Collectors.toList());

            return new RetrievalResult(results, (int) response.hits().total().value());
        } catch (IOException e) {
            throw new StepExecuteException(getName(), e);
        }
    }

    public static class Builder {
        private final EsClient esClient;
        private String indexName = "ingest_flow_test";
        private String vectorField = "_vector";
        private String contentField = "_content";
        private String keywordField = "_content";
        private RetrievalType retrievalType = RetrievalType.VECTOR;
        private int topK = 5;
        private InputResolver<float[]> inputResolver = new DefaultInputResolver.ByKeyResolver<>("query_vector", float[].class, "es-retrieval");
        private OutputResolver<RetrievalResult> outputResolver = new DefaultOutputResolver<>("retrieval_result");

        private Builder(EsClient esClient) {
            this.esClient = esClient;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder vectorField(String vectorField) {
            this.vectorField = vectorField;
            return this;
        }

        public Builder contentField(String contentField) {
            this.contentField = contentField;
            return this;
        }

        public Builder keywordField(String keywordField) {
            this.keywordField = keywordField;
            return this;
        }

        public Builder retrievalType(RetrievalType retrievalType) {
            this.retrievalType = retrievalType;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder inputResolver(InputResolver<float[]> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<RetrievalResult> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public EsRetrievalStep build() {
            return new EsRetrievalStep(esClient, indexName, vectorField, contentField,
                    keywordField, retrievalType, topK, inputResolver, outputResolver);
        }
    }

    public static class ConfigurableBuilder {
        private String host = "localhost";
        private int port = 9200;
        private String username;
        private String password;
        private String indexName = "ingest_flow_test";
        private String vectorField = "_vector";
        private String contentField = "_content";
        private String keywordField = "_content";
        private RetrievalType retrievalType = RetrievalType.VECTOR;
        private int topK = 5;
        private InputResolver<float[]> inputResolver = new DefaultInputResolver.ByKeyResolver<>("query_vector", float[].class, "es-retrieval");
        private OutputResolver<RetrievalResult> outputResolver = new DefaultOutputResolver<>("retrieval_result");

        private ConfigurableBuilder() {}

        public ConfigurableBuilder host(String host) {
            this.host = host;
            return this;
        }

        public ConfigurableBuilder port(int port) {
            this.port = port;
            return this;
        }

        public ConfigurableBuilder username(String username) {
            this.username = username;
            return this;
        }

        public ConfigurableBuilder password(String password) {
            this.password = password;
            return this;
        }

        public ConfigurableBuilder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public ConfigurableBuilder vectorField(String vectorField) {
            this.vectorField = vectorField;
            return this;
        }

        public ConfigurableBuilder contentField(String contentField) {
            this.contentField = contentField;
            return this;
        }

        public ConfigurableBuilder keywordField(String keywordField) {
            this.keywordField = keywordField;
            return this;
        }

        public ConfigurableBuilder retrievalType(RetrievalType retrievalType) {
            this.retrievalType = retrievalType;
            return this;
        }

        public ConfigurableBuilder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public ConfigurableBuilder inputResolver(InputResolver<float[]> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public ConfigurableBuilder outputResolver(OutputResolver<RetrievalResult> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public EsRetrievalStep build() {
            EsClient esClient;
            if (username != null && password != null) {
                esClient = new EsClient(host, port, username, password);
            } else {
                esClient = new EsClient(host, port);
            }
            return new EsRetrievalStep(esClient, indexName, vectorField, contentField,
                    keywordField, retrievalType, topK, inputResolver, outputResolver);
        }
    }
}
