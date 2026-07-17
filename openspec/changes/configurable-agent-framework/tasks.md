## 1. Project Setup

- [x] 1.1 Create aimi-rag-workflow parent pom.xml
- [x] 1.2 Configure Maven with Spring Boot 3.5.15-SNAPSHOT, JDK 21

## 2. aimi-rag-core 公共抽象层

- [x] 2.1 Create aimi-rag-core module structure
- [x] 2.2 Implement `Input<T>` class with `getPayload()`, `getHeaders()`, `getHeader()`, `withHeader()` — immutable envelope for flow entry
- [x] 2.3 Implement Step<I,O> interface with `process(I)` returning `O`, `getName()`, `getType()`, `getCategory()`, `canProcess()`
- [x] 2.4 Implement `StepType` enum (INPUT, CHUNKING, EMBEDDING, STORAGE)
- [x] 2.5 Implement `FlowCategory` enum (INGEST, QUERY, DELTA)
- [x] 2.6 Implement @StepOrder annotation for automatic ordering
- [x] 2.7 Implement FlowException for error handling

## 3. aimi-rag-ingest 子项目

- [x] 3.1 Create aimi-rag-ingest module (depends on aimi-rag-core)
- [x] 3.2 Implement data model classes: Chunk, Embedding, StorageItem, IngestResult, StorageResult
- [x] 3.3 Implement IngestFlow<T> generic interface and IngestFlowBuilder<T> (staged builder)
- [x] 3.4 Implement InputStep<T> interface (extends Step<Input<T>, ?>)
- [x] 3.5 Implement StringInputStep implementing InputStep<List<String>>
- [x] 3.6 Implement ChunkingStep interface (Step<List<String>, List<Chunk>>)
- [x] 3.7 Implement FixedSizeChunkingStep with builder (chunkSize + overlap)
- [x] 3.8 Implement RecursiveCharacterChunkingStep with builder (chunkSize + overlap + custom separators)
- [x] 3.9 Implement IngestEmbeddingStep interface (Step<List<Chunk>, List<Embedding>>) with default identity
- [x] 3.10 Implement StorageStep<T> interface (Step<List<T>, StorageResult>)
- [x] 3.11 Implement StorageItemConverter @FunctionalInterface and DefaultStorageItemConverter
- [x] 3.12 Write unit tests for IngestFlow

## 4. aimi-rag-es 子项目

- [x] 4.1 Create aimi-rag-es module (depends on aimi-rag-ingest)
- [x] 4.2 Implement EsClient class with exists(), createIndex(), deleteIndex(), bulk() methods
- [x] 4.3 Implement IndexStrategy enum (CREATE_IF_NOT_EXISTS, STRICT, DROP_AND_CREATE, APPEND_ONLY)
- [x] 4.4 Implement EsStorageStep implementing StorageStep<StorageItem> with Builder
- [x] 4.5 Implement runtime instanceof check for Chunk vs Embedding in process()
- [x] 4.6 Write unit tests for EsClient and EsStorageStep

## 5. aimi-rag-open-ai 子项目

- [x] 5.1 Create aimi-rag-open-ai module
- [x] 5.2 Add spring-ai-starter-model-openai dependency
- [x] 5.3 Create OpenAiEmbeddingConfig with EmbeddingModel Bean (manual, @ConditionalOnMissingBean)
- [x] 5.4 Write unit tests for OpenAiEmbeddingConfig

## 6. aimi-rag-embedding 子项目

- [x] 6.1 Create aimi-rag-embedding module (depends on aimi-rag-ingest, aimi-rag-open-ai)
- [x] 6.2 Implement IngestOpenAiEmbeddingStep (implements IngestEmbeddingStep, uses EmbeddingModel directly)
- [x] 6.3 Write unit tests for IngestOpenAiEmbeddingStep

## 7. Integration

- [x] 7.1 Create sample Java Config class for IngestFlow (SampleConfig)
- [x] 7.2 Write integration tests for IngestFlow (text + vector paths, IngestFlowIntegrationTest)
- [ ] 7.3 Document SDK usage guide for developers
- [ ] 7.4 Document how to create custom Step implementations

## 8. Future Work

- [ ] 8.1 Implement QueryFlow and QueryFlowBuilder
- [ ] 8.2 Implement DeltaFlow and DeltaFlowBuilder
- [ ] 8.3 Add YAML auto-configuration support (@ConfigurationProperties + @ConditionalOnProperty)
- [ ] 8.4 Add more chunking strategies (Semantic, Document-Aware, Hierarchical)
