package io.github.aimi.rag.workflow.es.client;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure Elasticsearch proxy — connection, CRUD operations only.
 * No business logic, no types outside the ES domain.
 */
public class EsClient {

    private final ElasticsearchClient client;

    public EsClient(String host, int port) {
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"));
        RestClient restClient = builder.build();

        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
    }

    public EsClient(String host, int port, String username, String password) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "https"))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));

        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
    }

    public ElasticsearchClient getClient() {
        return client;
    }

    // -- index operations --

    /**
     * Check whether an index exists.
     */
    public boolean exists(String indexName) throws IOException {
        return client.indices().exists(e -> e.index(indexName)).value();
    }

    /**
     * Create an index with optional dense_vector mapping.
     * @param dims vector dimensions, 0 to skip dense_vector mapping
     */
    public void createIndex(String indexName, int dims) throws IOException {
        CreateIndexRequest.Builder requestBuilder = new CreateIndexRequest.Builder().index(indexName);

        if (dims > 0) {
            requestBuilder.mappings(TypeMapping.of(m -> m
                    .properties("_vector", Property.of(p -> p
                            .denseVector(DenseVectorProperty.of(dv -> dv
                                    .dims(dims)
                                    .index(true)))))));
        }

        client.indices().create(requestBuilder.build());
    }

    /**
     * Delete an index.
     */
    public void deleteIndex(String indexName) throws IOException {
        client.indices().delete(new DeleteIndexRequest.Builder().index(indexName).build());
    }

    // -- document operations --

    /**
     * Bulk index raw documents.
     */
    public void bulk(String indexName, List<Map<String, Object>> docs) throws IOException {
        if (docs.isEmpty()) return;

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (Map<String, Object> doc : docs) {
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .document(doc)));
        }

        BulkResponse response = client.bulk(bulkBuilder.build());
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                throw new RuntimeException("Bulk index failed: " + item.error().reason());
            }
        }
    }

    // -- search operations --

    /**
     * Search all documents.
     */
    public SearchResponse<Map> searchAll(String indexName, int size) throws IOException {
        Query query = Query.of(q -> q.matchAll(m -> m));
        SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .query(query)
                .size(size)
                .build();
        return client.search(request, Map.class);
    }

    /**
     * Search by keyword using match query.
     */
    public SearchResponse<Map> searchByKeyword(String indexName, String field, String keyword, int size) throws IOException {
        Query query = Query.of(q -> q.match(m -> m.field(field).query(keyword)));
        SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .query(query)
                .size(size)
                .build();
        return client.search(request, Map.class);
    }

    /**
     * Search by keyword with pagination.
     */
    public SearchResponse<Map> searchByKeyword(String indexName, String field, String keyword, int from, int size) throws IOException {
        Query query = Query.of(q -> q.match(m -> m.field(field).query(keyword)));
        SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .query(query)
                .from(from)
                .size(size)
                .build();
        return client.search(request, Map.class);
    }

    /**
     * Search by vector using KNN search.
     */
    public SearchResponse<Map> searchByVector(String indexName, String vectorField, float[] vector, int k) throws IOException {
        KnnSearch knn = KnnSearch.of(kq -> kq
                .field(vectorField)
                .queryVector(toFloatList(vector))
                .k(k)
                .numCandidates(k * 10));
        SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .knn(knn)
                .size(k)
                .build();
        return client.search(request, Map.class);
    }

    /**
     * Search by vector with filter.
     */
    public SearchResponse<Map> searchByVector(String indexName, String vectorField, float[] vector, int k, Query filter) throws IOException {
        KnnSearch knn = KnnSearch.of(kq -> kq
                .field(vectorField)
                .queryVector(toFloatList(vector))
                .k(k)
                .filter(List.of(filter)));
        SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .knn(knn)
                .size(k)
                .build();
        return client.search(request, Map.class);
    }

    /**
     * Search with hybrid query (vector + keyword).
     */
    public SearchResponse<Map> searchHybrid(String indexName, String vectorField, float[] vector,
                                            String keywordField, String keyword, int k) throws IOException {
        KnnSearch knn = KnnSearch.of(kq -> kq
                .field(vectorField)
                .queryVector(toFloatList(vector))
                .k(k));
        Query keywordQuery = Query.of(q -> q.match(m -> m.field(keywordField).query(keyword)));
        SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .knn(knn)
                .query(keywordQuery)
                .size(k)
                .build();
        return client.search(request, Map.class);
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }
}
