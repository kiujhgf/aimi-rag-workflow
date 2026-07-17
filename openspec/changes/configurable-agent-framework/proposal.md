## Why

构建一个智能体工作流配置项目（aimi-rag-workflow），作为父项目整合多个子模块的能力，提供上层抽象供其他项目使用。通过配置驱动的设计，让智能体能够灵活调用数据处理、存储和向量化能力。

项目结构：
- **aimi-rag-workflow**: 父项目，提供工作流配置框架（类和方法）
- **aimi-rag-core**: 公共抽象层，提供 `Input<T>` / `Step<I,O>` / `StepType` / `FlowCategory` / `@StepOrder` / `FlowException`
- **aimi-rag-ingest**: 子项目，数据处理和向量数据库摄入，提供 `Chunk` / `Embedding` / `StorageItem` / `IngestFlow` / `IngestFlowBuilder` 及多种 Chunking 策略
- **aimi-rag-es**: 子项目，Elasticsearch存储，提供 `EsClient` / `EsStorageStep` / `IndexStrategy`
- **aimi-rag-open-ai**: 子项目，封装 Spring AI OpenAI 依赖，排除不必要的 AutoConfiguration
- **aimi-rag-embedding**: 子项目，向量化服务，提供 `IngestOpenAiEmbeddingStep`（直接使用 `EmbeddingModel`）

## What Changes

- **新增** aimi-rag-core: 公共抽象层，提供 `Input<T>` 输入信封、`Step<I,O>` 接口（纯数据变换）、`StepType` / `FlowCategory` 枚举、`@StepOrder` 注解、`FlowException`
- **新增** aimi-rag-open-ai: 封装 Spring AI OpenAI 依赖，集中排除 Chat/Audio/Image 等不必要 AutoConfiguration
- **整合** aimi-rag-ingest: 子项目，提供数据模型（Chunk, Embedding, StorageItem）、IngestFlow、分阶段 IngestFlowBuilder、多种 Chunking 策略（FixedSizeChunkingStep, RecursiveCharacterChunkingStep）、StorageItemConverter
- **整合** aimi-rag-es: 子项目，提供 EsClient、EsStorageStep（统一 text/vector 路径）、IndexStrategy 枚举
- **整合** aimi-rag-embedding: 子项目，提供 IngestOpenAiEmbeddingStep（直接使用 EmbeddingModel，无中间 Client）

## Capabilities

### New Capabilities

- aimi-rag-core: `Input<T>` / `Step<I,O>` / `StepType` / `FlowCategory` / `@StepOrder` / `FlowException`
- aimi-rag-open-ai: 封装 Spring AI OpenAI，通过 `@Configuration` + 排除 AutoConfiguration 精简容器，仅暴露 `EmbeddingModel` Bean
- aimi-rag-ingest: `StorageItem` 接口（Chunk/Embedding 共享）、`StorageItemConverter`（可插拔文档转换）、Fixed-Size + Recursive 两种 Chunking
- aimi-rag-es: `EsStorageStep`（单类处理 text/vector，运行时 instanceof）、`IndexStrategy` 枚举、Builder 模式
- 所有有参 Step 统一 Builder 模式

### Modified Capabilities

- `aimi-rag-ingest`: 作为aimi-rag-workflow子项目，提供数据处理类和方法
- `aimi-rag-es`: 作为aimi-rag-workflow子项目，提供Elasticsearch存储类和方法
- `aimi-rag-embedding`: 作为aimi-rag-workflow子项目，提供向量化类和方法

## Impact

- **代码影响**: aimi-rag-workflow作为父项目，整合 aimi-rag-core + 四个子项目
- **依赖**: Spring Boot、Jackson、Spring AI OpenAI、Elasticsearch Java Client
- **使用方式**: 提供类和方法（SDK），供其他项目集成使用
- **文档**: 需要提供集成指南和使用文档
