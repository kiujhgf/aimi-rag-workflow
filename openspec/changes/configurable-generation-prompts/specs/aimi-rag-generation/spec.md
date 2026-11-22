## ADDED Requirements

### Requirement: 可配置的 systemPrompt 与 userPrompt

The aimi-rag-generation module SHALL allow `systemPrompt` and `userPrompt` to be configured on `ChatGenerationStep` through both `builder(ChatModel)` and `builder()`.

#### Scenario: 两者都配置

- **WHEN** `messages` 为空，且配置了 `systemPrompt` 与 `userPrompt`
- **THEN** 发送给模型的消息依次为 `SystemMessage(systemPrompt)`、`UserMessage(userPrompt)`

#### Scenario: 仅配置 userPrompt

- **WHEN** `messages` 为空，只配置了 `userPrompt`
- **THEN** 发送给模型的消息只有一条 `UserMessage(userPrompt)`

#### Scenario: messages 非空

- **WHEN** `FlowContext` 的 `messages` 非空
- **THEN** 原样透传该列表，配置的 `systemPrompt` / `userPrompt` 不生效

### Requirement: prompt 占位符替换

Both prompts SHALL support `{key}` placeholders resolved from `FlowContext` at execution time.

#### Scenario: 占位符命中

- **WHEN** `userPrompt` 为 `问题：{question}` 且 `FlowContext` 中 `question` 为 `什么是 RAG`
- **THEN** 最终文本为 `问题：什么是 RAG`

#### Scenario: 占位符未命中

- **WHEN** 占位符对应的 key 在 `FlowContext` 中不存在
- **THEN** 保留原始 `{key}` 文本，不抛异常

#### Scenario: 含花括号的普通文本

- **WHEN** prompt 中包含 JSON 片段如 `{"a": 1}`
- **THEN** 该片段不被改写（仅 `{标识符}` 形式参与替换）

### Requirement: 空输入处理

`ChatGenerationStep` SHALL treat a missing or empty `messages` as empty, and SHALL fail fast when no prompt is configured either.

#### Scenario: messages 缺失

- **WHEN** `FlowContext` 中不存在 `messages` key
- **THEN** 按空列表处理，继续走 prompt 组装逻辑

#### Scenario: 三者皆空

- **WHEN** `messages` 为空，且未配置 `systemPrompt` 与 `userPrompt`
- **THEN** 抛出 `StepExecuteException`，不调用模型
