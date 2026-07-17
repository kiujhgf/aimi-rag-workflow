## ADDED Requirements

### Requirement: EsClient class
The aimi-rag-es module SHALL provide `EsClient` class for Elasticsearch operations.

#### Scenario: Check index existence
- **WHEN** `EsClient.exists(indexName)` is called
- **THEN** returns true if the index exists in Elasticsearch

#### Scenario: Create index
- **WHEN** `EsClient.createIndex(indexName, dims)` is called
- **THEN** creates an index in Elasticsearch, with `dims` specifying dense_vector dimensions (0 = no vectors)

#### Scenario: Delete index
- **WHEN** `EsClient.deleteIndex(indexName)` is called
- **THEN** removes the index from Elasticsearch

#### Scenario: Bulk index documents
- **WHEN** `EsClient.bulk(indexName, docs)` is called with a list of document maps
- **THEN** all documents are bulk indexed into the specified index

### Requirement: IndexStrategy enum
The aimi-rag-es module SHALL provide `IndexStrategy` enum for controlling index lifecycle behavior.

#### Scenario: IndexStrategy values
- **WHEN** `IndexStrategy` is referenced
- **THEN** values include `CREATE_IF_NOT_EXISTS`, `STRICT`, `DROP_AND_CREATE`, `APPEND_ONLY`

#### Scenario: CREATE_IF_NOT_EXISTS
- **WHEN** EsStorageStep uses `CREATE_IF_NOT_EXISTS`
- **THEN** creates the index if it does not exist, otherwise uses it as-is

#### Scenario: STRICT
- **WHEN** EsStorageStep uses `STRICT`
- **THEN** throws an exception if the index does not exist

#### Scenario: DROP_AND_CREATE
- **WHEN** EsStorageStep uses `DROP_AND_CREATE`
- **THEN** deletes the index (if it exists) and recreates it fresh

#### Scenario: APPEND_ONLY
- **WHEN** EsStorageStep uses `APPEND_ONLY`
- **THEN** appends documents without checking or creating the index

### Requirement: EsStorageStep
The aimi-rag-es module SHALL provide `EsStorageStep` implementing `StorageStep<StorageItem>`, handling both text (Chunk) and vector (Embedding) data. The type is determined at runtime via `instanceof`.

#### Scenario: Builder with defaults
- **WHEN** `EsStorageStep.builder(esClient).build()` is called
- **THEN** creates a step with default index name `"aimi_rag_default"`, dims=0, and strategy `CREATE_IF_NOT_EXISTS`

#### Scenario: Builder with dims
- **WHEN** `EsStorageStep.builder(esClient).dims(1536).build()` is called
- **THEN** creates a step configured for 1536-dimensional vectors

#### Scenario: Builder with custom index and strategy
- **WHEN** `EsStorageStep.builder(esClient).indexName("my_index").indexStrategy(IndexStrategy.STRICT).build()` is called
- **THEN** creates a step targeting "my_index" with STRICT index strategy

#### Scenario: Text storage (no vectors)
- **WHEN** `EsStorageStep.process(List<Chunk>)` is called
- **THEN** chunks are converted to ES documents with content and metadata, no `_vector` field, and bulk indexed

#### Scenario: Vector storage (with vectors)
- **WHEN** `EsStorageStep.process(List<Embedding>)` is called
- **THEN** embeddings are converted to ES documents with content, metadata, and `_vector` field, and bulk indexed

#### Scenario: Custom StorageItemConverter
- **WHEN** `EsStorageStep.builder(esClient).converter(customConverter).build()` is called
- **THEN** the custom converter is used for all StorageItem → Map conversions
