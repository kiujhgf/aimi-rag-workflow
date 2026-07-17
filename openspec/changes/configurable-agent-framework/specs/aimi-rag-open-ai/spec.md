## ADDED Requirements

### Requirement: OpenAiEmbeddingConfig
The aimi-rag-open-ai module SHALL provide `OpenAiEmbeddingConfig` as a `@Configuration` class that manually creates `EmbeddingModel` Bean, excluding unnecessary Spring AI AutoConfiguration (Audio/Chat/Image/Moderation).

#### Scenario: EmbeddingModel Bean created
- **WHEN** `OpenAiEmbeddingConfig` is loaded
- **THEN** a single `EmbeddingModel` Bean (via `OpenAiEmbeddingModel`) is registered in the Spring container

#### Scenario: AutoConfiguration excluded
- **WHEN** aimi-rag-open-ai is on the classpath
- **THEN** Spring AI's AudioSpeech, AudioTranscription, Chat, Embedding, Image, Moderation AutoConfiguration classes are excluded

#### Scenario: Conditional on missing Bean
- **WHEN** user defines their own `EmbeddingModel` Bean
- **THEN** `OpenAiEmbeddingConfig` does not create a duplicate Bean (`@ConditionalOnMissingBean`)

### Requirement: OpenAiApi Bean
The aimi-rag-open-ai module SHALL provide `OpenAiApi` Bean for connecting to OpenAI-compatible APIs.

#### Scenario: OpenAiApi configured
- **WHEN** `OpenAiApi` Bean is created with API key
- **THEN** it can communicate with OpenAI-compatible embedding endpoints
