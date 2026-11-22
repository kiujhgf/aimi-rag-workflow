# 设计文档：Generation Step 通用化

## Context

改造前 `OpenAiGenerationStep` 继承 `GenerationStep extends AbstractStep<RetrievalResult, QueryResult>`：

- 输入固定为检索结果，输出固定为 `QueryResult`
- `doProcess` 内同时完成 prompt 组装（拼接 `context`、填充 `{question}`）与模型调用
- `query_text` context key 硬编码
- system prompt 使用 `SystemPromptTemplate`，user prompt 使用 `String.replace`，两套渲染机制
- 类名为 `OpenAi*`，但 `builder(ChatModel)` 实际支持任意 provider

这使该 step 无法用于非 RAG 场景，也让 I/O 映射逻辑与模型调用混在一起。

## Goals / Non-Goals

**Goals:**

- `GenerationStep` 契约改为 `List<Message>` → `String`，与具体 RAG 模型解耦
- `ChatGenerationStep` 接受任意 `ChatModel`，`doProcess` 只负责一次模型调用
- 默认 resolver 直接从 context 读取 `messages`、写入 `answer`
- 删除 generation 模块对 `RetrievalResult` / `QueryResult` 的依赖

**Non-Goals:**

- 不提供 prompt 模板组装能力（由调用方组装 `List<Message>`）
- 不新增 temperature / maxTokens 等生成参数
- 不修改检索链路（`RetrievalStep` / `EsRetrievalStep` 保持不变）
- 不清理 `QueryResult`（仅在本变更中失去引用）

## Decisions

### 决策 1：输入使用 `List<Message>` 而非 `Prompt`

`doProcess` 接收消息列表，内部包装为 `new Prompt(input)`。

- 理由：`Prompt` 只是模型调用的包装细节，暴露 `List<Message>` 更贴近调用方实际组装的内容
- 备选：输入 `Prompt` —— 灵活性相近，但把包装职责外移，收益不明显，未采用

### 决策 2：默认 resolver 为 `messages` → `answer`

| 方向 | key | 类型 |
|------|-----|------|
| 输入 | `messages` | `List<Message>` |
| 输出 | `answer` | `String` |

- 理由：零配置即可用于任意 LLM 场景；step 只做「消息进、文本出」
- 备选：默认 resolver 自动从 `RetrievalResult` 组装消息 —— 会重新绑定 RAG 模型，否决

### 决策 3：删除 RAG 专用 resolver

删除 `RagMessageInputResolver` 与 `QueryResultOutputResolver`。

- 理由：这两个组件让 generation 模块反向依赖 `RetrievalResult` / `QueryResult`，与「provider 无关」目标冲突；prompt 组装属于调用方业务
- 备选：保留为可选组件 —— 造成模块职责混杂，且默认路径不会被使用，否决

### 决策 4：类名 `ChatGenerationStep`

- 理由：与 `builder(ChatModel)` 的实际能力一致；OpenAI 只是 `ChatModel` 的一个实现
- 备选：保留 `OpenAiGenerationStep` 作为子类 —— 无行为差异，徒增类数量，否决

### 决策 5：依赖放置

- `aimi-rag-query` 仅引入 `spring-ai-model`（`Message` / `Prompt` API），不引入任何 provider starter
- `aimi-rag-generation` 保留 `spring-ai-starter-model-openai`，用于 `ConfigurableBuilder` 的便捷构建

### 决策 6：保留 `ConfigurableBuilder`

继续支持 `apiKey` / `baseUrl` / `model` 直接构造 `OpenAiChatModel`，降低无 Spring 环境的使用成本；由于不再持有 prompt 模板，builder 不再暴露 `systemPrompt` / `userPromptTemplate`。

## Risks / Trade-offs

| 风险 / 权衡 | 缓解措施 |
|------|---------|
| `GenerationStep` 泛型变更属 BREAKING | 调用方改用 `messages` / `answer`，迁移步骤见下 |
| prompt 组装责任外移，调用方需自行渲染模板 | 在文档/示例中给出 RAG 消息组装示例 |
| `QueryResult` 失去引用成为孤儿类 | 保留类定义，后续单独评估删除 |
| `aimi-rag-query` 新增 Spring AI 依赖 | 仅 `spring-ai-model` API 依赖，无 starter、无自动配置 |
| 删除 `systemPrompt` / `userPromptTemplate` 配置项 | 属 BREAKING，需在变更说明中明确 |

## Migration Plan

1. 将 `OpenAiGenerationStep` 替换为 `ChatGenerationStep`（`builder(ChatModel)` 或 `builder()`）
2. 调用方在 `FlowContext` 中写入 `messages`（`List<Message>`，自行完成模板渲染）
3. 从 `FlowContext` 的 `answer` 读取答案文本
4. 如需 `QueryResult`，由调用方在生成后自行包装
5. 回滚策略：恢复 `OpenAiGenerationStep` 及其 resolver 即可，无数据结构变更

## Open Questions

- `QueryResult` 是否直接删除，还是保留为对外 API
- 是否需要将 RAG 消息组装抽成独立可选组件（放在调用方模块，而非 generation 模块）
