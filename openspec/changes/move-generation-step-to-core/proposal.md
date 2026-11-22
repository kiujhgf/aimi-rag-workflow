# Proposal: 将 GenerationStep 上移到 core

## Why

`GenerationStep` 是一个 provider 无关的「消息进、文本出」的生成步骤契约，它只依赖
`List<Message>` / `String` 和 core 的 `AbstractStep` / resolver 体系，不依赖任何 RAG
特定模型（`RetrievalResult` / `QueryResult`）。把它放在 `aimi-rag-query` 里会导致：

1. 任何非 RAG 场景想复用一个「通用生成步骤」都要引入 `aimi-rag-query` 这个检索模型模块
2. `aimi-rag-generation` 为了继承 `GenerationStep` 不得不依赖 `aimi-rag-query`，模块依赖方向不合理
3. `GenerationStep` 与 `QueryEmbeddingStep`、`RetrievalStep`（它们都依赖 RAG 模型）混在同一层，职责边界不清

## What Changes

- **BREAKING** `GenerationStep` 从 `io.github.aimi.rag.workflow.query.step.generation`
  迁移到 `io.github.aimi.rag.workflow.core.step`
- `aimi-rag-core` 新增 `spring-ai-model` 依赖（提供 `Message` 类型）
- `aimi-rag-query` 移除 `spring-ai-model` 依赖（迁移后无其他消费者）
- `aimi-rag-generation` 的 `ChatGenerationStep` 改为继承 core 的 `GenerationStep`
- **BREAKING** `aimi-rag-generation` 移除对 `aimi-rag-query` 的依赖

## Capabilities

### New Capabilities

- `aimi-rag-core`：定义 provider 无关的 `GenerationStep` 抽象步骤契约

### Modified Capabilities

- `aimi-rag-query`：移除 `GenerationStep`（上移到 core）

## Impact

- **aimi-rag-core**：新增 `spring-ai-model` 依赖；新增 `GenerationStep`
- **aimi-rag-query**：删除 `GenerationStep`；移除 `spring-ai-model` 依赖
- **aimi-rag-generation**：`ChatGenerationStep` 改 import；移除 `aimi-rag-query` 依赖
- **调用方**：依赖 `io.github.aimi.rag.workflow.query.step.generation.GenerationStep`
  的代码需改为 `io.github.aimi.rag.workflow.core.step.GenerationStep`
