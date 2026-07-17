## ADDED Requirements

### Requirement: InputStep interface
The aimi-rag-ingest module SHALL provide `InputStep<T>` as interface extending `Step<Input<T>, ?>`.

#### Scenario: InputStep contract
- **WHEN** a class implements InputStep<T>
- **THEN** it converts `Input<T>` to a downstream-compatible type via `process(FlowContext)`

### Requirement: StringInputStep
The aimi-rag-ingest module SHALL provide `StringInputStep` implementing `InputStep<List<String>>` as default pass-through.

#### Scenario: StringInputStep pass-through
- **WHEN** `StringInputStep.process(FlowContext context)` is called
- **THEN** sets `texts` in context with input payload

### Requirement: IngestResult class
The aimi-rag-ingest module SHALL provide `IngestResult` for ingest flow results.

#### Scenario: IngestResult success
- **WHEN** IngestFlow completes successfully
- **THEN** IngestResult contains stored count and success status

### Requirement: StorageResult class
The aimi-rag-ingest module SHALL provide `StorageResult` for storage step results.

#### Scenario: StorageResult
- **WHEN** StorageStep completes
- **THEN** StorageResult contains the number of stored items

### Requirement: Data model classes
The aimi-rag-ingest module SHALL provide data model classes: Chunk, Embedding, StorageItem.

#### Scenario: Chunk model
- **WHEN** `Chunk` is created with content and metadata
- **THEN** contains text content and optional key-value metadata map, implements `StorageItem`

#### Scenario: Embedding model
- **WHEN** `Embedding` is created
- **THEN** contains float array vector and associated source Chunk reference, implements `StorageItem`

#### Scenario: StorageItem interface
- **WHEN** `StorageItem` is referenced
- **THEN** defines `getContent()` and `getMetadata()`, shared by Chunk and Embedding

### Requirement: ChunkingStep interface
The aimi-rag-ingest module SHALL provide `ChunkingStep` as interface implementing `Step<List<String>, List<Chunk>>`.

#### Scenario: FixedSizeChunkingStep
- **WHEN** `FixedSizeChunkingStep.builder(chunkSize).overlap(n).build()` is created
- **THEN** splits text into chunks of `chunkSize` characters with `n` characters overlap between adjacent chunks

#### Scenario: RecursiveCharacterChunkingStep
- **WHEN** `RecursiveCharacterChunkingStep.builder(chunkSize).overlap(n).build()` is created
- **THEN** splits text by separators in priority order (paragraph → line → space → character), recursively breaking chunks that exceed `chunkSize`, with optional overlap

### Requirement: IngestEmbeddingStep interface
The aimi-rag-ingest module SHALL provide `IngestEmbeddingStep` as interface extending `Step<List<Chunk>, List<Embedding>>`, with default `getName()`, `getType()`, and `getCategory()`.

#### Scenario: IngestEmbeddingStep integration
- **WHEN** `IngestOpenAiEmbeddingStep` implements `IngestEmbeddingStep`
- **THEN** it converts `List<Chunk>` to `List<Embedding>` using an `EmbeddingModel`, inheriting type-safe defaults

### Requirement: StorageStep interface
The aimi-rag-ingest module SHALL provide `StorageStep<T extends StorageItem>` as a generic interface extending `Step<List<T>, StorageResult>`.

#### Scenario: StorageStep contract
- **WHEN** a class implements `StorageStep<StorageItem>` (or `StorageStep<Chunk>`, `StorageStep<Embedding>`)
- **THEN** it provides `getName()` and `process(FlowContext)` returning `StorageResult`

### Requirement: StorageItemConverter interface
The aimi-rag-ingest module SHALL provide `StorageItemConverter` as a `@FunctionalInterface` for converting `StorageItem` to a document map.

#### Scenario: DefaultStorageItemConverter
- **WHEN** `DefaultStorageItemConverter.toDoc(item, context)` is called
- **THEN** copies metadata and content, and adds `_vector` field for `Embedding` items

#### Scenario: Custom converter
- **WHEN** a custom `StorageItemConverter` is provided to `EsStorageStep`
- **THEN** the custom conversion logic is used instead of the default

### Requirement: Flow integration
The aimi-rag-ingest module SHALL use `Flow` and `FlowBuilder` from aimi-rag-core for constructing and executing ingest pipelines.

#### Scenario: Text path — chunking then storage
- **WHEN** `FlowBuilder.create().step(inputStep).step(chunkingStep).step(storageStep).build()` is called
- **THEN** returns a `Flow` with input → chunking → storage pipeline

#### Scenario: Vector path — chunking, embedding, then storage
- **WHEN** `FlowBuilder.create().step(inputStep).step(chunkingStep).step(embeddingStep).step(storageStep).build()` is called
- **THEN** returns a `Flow` with input → chunking → embedding → storage pipeline

#### Scenario: Execute text path
- **WHEN** `Flow.execute(FlowContext)` is called with input
- **THEN** returns `FlowContext` containing `storage_result`
- **AND** `IngestResult` can be extracted from context