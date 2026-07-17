package io.github.aimi.rag.workflow.integration;


import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

import io.github.aimi.rag.workflow.embedding.step.IngestOpenAiEmbeddingStep;
import io.github.aimi.rag.workflow.es.client.EsClient;
import io.github.aimi.rag.workflow.es.client.IndexStrategy;
import io.github.aimi.rag.workflow.es.step.EsStorageStep;
import io.github.aimi.rag.workflow.core.flow.Flow;
import io.github.aimi.rag.workflow.core.flow.FlowBuilder;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.IngestResult;
import io.github.aimi.rag.workflow.ingest.model.StorageResult;
import io.github.aimi.rag.workflow.ingest.step.chunking.FixedSizeChunkingStep;
import io.github.aimi.rag.workflow.ingest.step.input.StringInputStep;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TestRemoteConn {

    @Test
    public void testEmbAndEs() throws IOException, InterruptedException {
        EsClient esClient = new EsClient("192.168.1.6", 9200);

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey("sk-39b0268da1b24120aaf91d55b629339c");
        apiBuilder.baseUrl("https://llm-u81mf9psloy2h05c.cn-beijing.maas.aliyuncs.com/compatible-mode");
        OpenAiApi openAiApi = apiBuilder.build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-v4")
                .dimensions(1024)
                .build();

        EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);

        Flow flow = FlowBuilder.create()
                .step(StringInputStep.builder().build())
                .step(FixedSizeChunkingStep.builder(100)
                        .overlap(50)
                        .build())
                .step(IngestOpenAiEmbeddingStep.builder(embeddingModel).build())
                .step(EsStorageStep.builder(esClient)
                        .dims(1024)
                        .indexName("ingest_flow_test")
                        .indexStrategy(IndexStrategy.DROP_AND_CREATE)
                        .build())
                .build();

        FlowContext context = FlowContext.create();
        context.set("input", List.of("这家餐厅做的红烧肉非常好吃"));
        context = flow.execute(context);

        StorageResult storageResult = context.get("storage_result", StorageResult.class);
        IngestResult rst = new IngestResult(storageResult != null ? storageResult.getStoredCount() : 0, true);
        System.out.println(rst);

        Thread.sleep(2000);

        float[] embed = embeddingModel.embed("这家餐厅的红烧肉味道很赞");
        SearchResponse<Map> resp = esClient.searchByVector("ingest_flow_test", "_vector", embed, 5);
        long totalHits = resp.hits().total().value();

        System.out.println("total hits: " + totalHits);
        assert totalHits > 0 : "Vector search should return results";

        for (Hit<Map> hit : resp.hits().hits()) {
            String content = (String) hit.source().get("content");
            Double score = hit.score();
            System.out.println("{ score: " + score + ", content:" + content + "}");
        }
    }
}