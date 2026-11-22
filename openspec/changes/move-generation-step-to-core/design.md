# 设计文档：GenerationStep 上移到 core

## Context

`GenerationStep` 是 `AbstractStep<List<Message>, String>` 的抽象基类，只表达「接收已组装
好的消息、返回原始答案文本」的契约。它不依赖任何 RAG 模型，目前却位于 `aimi-rag-query`
模块的 `query.step.generation` 包下，导致：

- `aimi-rag-generation` 仅为了继承它而依赖 `aimi-rag-query`
- `aimi-rag-query` 仅为了它而引入 `spring-ai-model`

## Goals / Non-Goals

**Goals:**

- `GenerationStep` 迁入 `aimi-rag-core` 的 `core.step` 包
- `aimi-rag-core` 引入 `spring-ai-model`（仅 API，非 starter/自动配置）
- 解除 `aimi-rag-generation` 对 `aimi-rag-query` 的依赖

**Non-Goals:**

- 不改动 `GenerationStep` 的契约（仍为 `List<Message>` → `String`）
- 不迁移 `QueryEmbeddingStep` / `RetrievalStep`（它们依赖 RAG 模型，留在 query 合理）
- 不改动 `ChatGenerationStep` 的行为

## Decisions

### 决策 1：目标包为 `core.step`（扁平，与 `AbstractStep`/`StartStep` 同层）

core 的抽象步骤目前都平铺在 `core.step` 包，不设子包。`GenerationStep` 作为通用生成
步骤基类，与 `AbstractStep`、`StartStep`、`EndStep` 保持一致。

- 备选：`core.step.generation` 子包 —— 过度分层，且与其他 core 步骤不统一，否决。

### 决策 2：core 引入 `spring-ai-model`（仅 API）

`GenerationStep` 依赖 `org.springframework.ai.chat.messages.Message`。引入的是
`spring-ai-model`（纯 API：`Message`/`Prompt`/`ChatModel`），不含 provider starter 与
自动配置，不会把 core 拖入任何模型实现。

- 备选：将 `GenerationStep` 泛型化为 `GenerationStep<I>` 摆脱 Spring AI 类型 —— 会削弱
  「消息进、文本出」这一具体契约，且框架整体已采纳 Spring AI 作为模型抽象，否决。

### 决策 3：解除 generation 对 query 的依赖

迁移后 `aimi-rag-generation` 不再引用 `query` 包，移除 `aimi-rag-query` 依赖。

### 决策 4：query 移除 `spring-ai-model`

迁移后 query 模块再无 `Message` 等 Spring AI 类型引用，移除该依赖。

## Risks / Trade-offs

| 风险 / 权衡 | 缓解措施 |
|------|---------|
| core 引入 Spring AI 依赖，弱化「纯流程引擎」定位 | 仅 `spring-ai-model` API，无实现/自动配置；与框架整体采用 Spring AI 抽象一致 |
| `GenerationStep` 包迁移属 BREAKING | 迁移说明见 proposal；仓库内唯一引用方 `ChatGenerationStep` 一并更新 |
| 移除 generation→query 依赖可能遗漏其他 query 引用 | 全量 grep 确认仅 `GenerationStep` 一处引用 |

## Migration Plan

1. `GenerationStep` 文件迁至 `core.step`，改 package、去掉对同包 `AbstractStep` 的 import
2. `aimi-rag-core/pom.xml` 增加 `spring-ai-model`
3. `aimi-rag-query/pom.xml` 移除 `spring-ai-model`
4. `ChatGenerationStep` 改 import
5. `aimi-rag-generation/pom.xml` 移除 `aimi-rag-query` 依赖
6. 编译 + 单测验证

## Open Questions

- 无
