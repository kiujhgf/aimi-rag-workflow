package io.github.aimi.rag.workflow.es.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.exception.StepValidationException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.Step;
import io.github.aimi.rag.workflow.es.client.EsClient;
import io.github.aimi.rag.workflow.es.client.IndexStrategy;
import io.github.aimi.rag.workflow.es.converter.DefaultStorageItemConverter;
import io.github.aimi.rag.workflow.es.converter.StorageItemConverter;
import io.github.aimi.rag.workflow.ingest.model.StorageItem;
import io.github.aimi.rag.workflow.ingest.model.StorageResult;
import io.github.aimi.rag.workflow.ingest.step.storage.StorageStep;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EsStorageStep extends StorageStep<StorageItem> {

    private static final String DEFAULT_INDEX = "aimi_rag_default";

    private final EsClient esClient;
    private final String indexName;
    private final int dims;
    private final IndexStrategy indexStrategy;
    private final StorageItemConverter converter;

    private EsStorageStep(EsClient esClient, int dims, String indexName, IndexStrategy indexStrategy,
                          StorageItemConverter converter, InputResolver<List<StorageItem>> inputResolver,
                          OutputResolver<StorageResult> outputResolver) {
        super(inputResolver, outputResolver);
        this.esClient = esClient;
        this.indexName = indexName;
        this.dims = dims;
        this.indexStrategy = indexStrategy;
        this.converter = converter;
    }

    public static Builder builder(EsClient esClient) {
        return new Builder(esClient);
    }

    @Override
    public String getName() {
        return "es-storage";
    }

    @Override
    protected StorageResult doProcess(List<StorageItem> input, FlowContext context) throws StepExecuteException {
        try {
            List<Map<String, Object>> docs = new ArrayList<>();
            for (StorageItem item : input) {
                docs.add(converter.toDoc(item, context));
            }
            ensureIndex();
            esClient.bulk(indexName, docs);
            return new StorageResult(docs.size());
        } catch (IOException e) {
            throw new StepExecuteException(getName(), "Failed to bulk index to ES", e);
        } catch (Exception e) {
            throw new StepExecuteException(getName(), e);
        }
    }

    private void ensureIndex() throws IOException {
        boolean exists = esClient.exists(indexName);

        switch (indexStrategy) {
            case CREATE_IF_NOT_EXISTS -> {
                if (!exists) {
                    esClient.createIndex(indexName, dims);
                }
            }
            case STRICT -> {
                if (!exists) {
                    throw new IllegalStateException(
                            "Index '" + indexName + "' does not exist. " +
                            "STRICT strategy requires the index to be provisioned before use.");
                }
            }
            case DROP_AND_CREATE -> {
                if (exists) {
                    esClient.deleteIndex(indexName);
                }
                esClient.createIndex(indexName, dims);
            }
            case APPEND_ONLY -> {
            }
        }
    }

    public static class Builder {
        private final EsClient esClient;
        private int dims = 0;
        private String indexName = DEFAULT_INDEX;
        private IndexStrategy indexStrategy = IndexStrategy.CREATE_IF_NOT_EXISTS;
        private StorageItemConverter converter = new DefaultStorageItemConverter();
        private InputResolver<List<StorageItem>> inputResolver = new DefaultInputResolver.ByMultipleKeysResolver<>(new String[]{"embeddings", "chunks"}, List.class, "es-storage");
        private OutputResolver<StorageResult> outputResolver = new DefaultOutputResolver<>("storage_result");

        private Builder(EsClient esClient) {
            this.esClient = esClient;
        }

        public Builder dims(int dims) {
            this.dims = dims;
            return this;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder indexStrategy(IndexStrategy indexStrategy) {
            this.indexStrategy = indexStrategy;
            return this;
        }

        public Builder converter(StorageItemConverter converter) {
            this.converter = converter;
            return this;
        }

        public Builder inputResolver(InputResolver<List<StorageItem>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<StorageResult> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public EsStorageStep build() {
            return new EsStorageStep(esClient, dims, indexName, indexStrategy, converter, inputResolver, outputResolver);
        }
    }
}