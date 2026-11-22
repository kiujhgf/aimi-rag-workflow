# Proposal: Generation Step 通用化

## Why

现有 `OpenAiGenerationStep` 与 RAG 场景和 OpenAI 实现强耦合，无法复用：

1. 输入/输出类型写死为 `RetrievalResult` / `QueryResult`，非 RAG 场景无法使用
2. `query_text` 的 context key 硬编码，prompt 变量固定为 `{context}` / `{question}`
3. prompt 组装（拼接上下文、填充变量）属于业务逻辑，却写在 `doProcess` 中
4. 类名带 `OpenAi`，但 `builder(ChatModel)` 实际支持任意 provider，命名与能力不符

## What Changes

- **BREAKING** `GenerationStep` 泛型由 `AbstractStep<RetrievalResult, QueryResult>` 改为 `AbstractStep<List<Message>, String>`
- 删除 `OpenAiGenerationStep`，新增 provider 无关的 `ChatGenerationStep`，仅执行 `chatModel.call(new Prompt(messages))` 并返回答案文本
- 默认输入 resolver 从 context 的 `messages` key 读取 `List<Message>`
- 默认输出 resolver 将答案文本写入 context 的 `answer` key
- **BREAKING** 删除 `RagMessageInputResolver`、`QueryResultOutputResolver`；prompt 组装与结果包装改由调用方负责
- `aimi-rag-query` 增加 `spring-ai-model` 依赖（`Message` / `Prompt` 类型）
- `QueryResult` 模型在生成链路中不再被引用

## Capabilities

### New Capabilities

- `aimi-rag-query`：定义与 provider 无关的 generation step 契约（`List<Message>` → `String`）
- `aimi-rag-generation`：提供基于任意 `ChatModel` 的通用聊天生成实现

### Modified Capabilities

无既有 spec 需要增量修改（`openspec/specs/` 尚未建立）。

## Impact

- **aimi-rag-query**：`GenerationStep` 泛型变更；新增 `spring-ai-model` 依赖
- **aimi-rag-generation**：删除 `OpenAiGenerationStep` 及两个 RAG 专用 resolver，新增 `ChatGenerationStep`
- **调用方**：需自行组装 `List<Message>` 写入 `messages`，并从 `answer` 读取结果；结果包装（如 `QueryResult`）由调用方完成
- **模型**：`QueryResult` 变为无引用类，后续可清理；`RetrievalResult` 仍被检索链路使用，保留
