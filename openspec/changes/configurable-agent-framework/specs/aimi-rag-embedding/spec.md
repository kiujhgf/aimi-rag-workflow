## ADDED Requirements

### Requirement: IngestOpenAiEmbeddingStep
The aimi-rag-embedding module SHALL provide `IngestOpenAiEmbeddingStep` implementing `IngestEmbeddingStep`, using `EmbeddingModel` (from aimi-rag-open-ai) directly for vector generation without an intermediate client.

#### Scenario: Builder creation
- **WHEN** `IngestOpenAiEmbeddingStep.builder(embeddingModel).build()` is called
- **THEN** creates a step configured with the given `EmbeddingModel`

#### Scenario: Generate embeddings
- **WHEN** `IngestOpenAiEmbeddingStep.process(List<Chunk>)` is called
- **THEN** chunks are converted to `List<Embedding>` using the configured embedding model, each embedding containing the source chunk and its float array vector

#### Scenario: Step identity
- **WHEN** `IngestOpenAiEmbeddingStep.getName()` is called
- **THEN** returns `"open-ai-embedding"`
