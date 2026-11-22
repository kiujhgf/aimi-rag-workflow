## ADDED Requirements

### Requirement: Provider 无关的聊天生成步骤

The aimi-rag-generation module SHALL provide `ChatGenerationStep` that accepts any Spring AI `ChatModel`
(wrapped into `ChatClient` internally) and produces the raw answer text (or a cold `Flux<String>` for streaming).

#### Scenario: 接受任意 ChatModel

- **WHEN** 使用 `ChatGenerationStep.builder(chatClient)` 构建步骤
- **THEN** 步骤使用传入的 `ChatClient`，不限定具体 provider

#### Scenario: 返回答案文本

- **WHEN** 非流式模式下输入消息列表非空且模型调用成功
- **THEN** `doProcess` 返回 `chatClient.prompt().messages(messages).call().content()`

#### Scenario: 流式模式返回 Flux

- **WHEN** 通过 builder 设置 `streaming(true)`
- **THEN** `doProcess` 不发起请求，只把冷 `Flux<String>`（`chatClient.prompt().messages(messages).stream().content()`）放入 context 的 `stream` key，返回 null

### Requirement: 默认 Context Key

`ChatGenerationStep` SHALL read the input messages from the `messages` context key and write the answer to the `answer`
context key by default (or to `stream` key in streaming mode).

#### Scenario: 默认读取 messages

- **WHEN** `FlowContext` 中 `messages` 为 `List<Message>`
- **THEN** 步骤以该列表作为模型输入

#### Scenario: 默认写入 answer

- **WHEN** 非流式模式下模型返回答案文本
- **THEN** `FlowContext` 的 `answer` key 被设置为该文本

#### Scenario: 流式模式写入 stream

- **WHEN** 流式模式启用
- **THEN** `FlowContext` 的 `stream` key 被设置为冷 `Flux<String>`，`answer` 不写入

### Requirement: 可自定义的输入输出映射

`ChatGenerationStep` SHALL allow callers to override both the input resolver and the output resolver.

#### Scenario: 自定义输入 resolver

- **WHEN** builder 设置了自定义 `InputResolver<List<Message>>`
- **THEN** 步骤从自定义来源解析消息，不再读取 `messages`

#### Scenario: 自定义输出 resolver

- **WHEN** builder 设置了自定义 `OutputResolver<String>`
- **THEN** 步骤将答案文本交给该 resolver 处理，不再写入 `answer`
  （流式模式下 `writeOutput` 被跳过，自定义 resolver 不会被调用）

### Requirement: OpenAI 便捷构建

`ChatGenerationStep` SHALL provide a configurable builder that constructs an OpenAI `ChatModel` from `apiKey`,
`baseUrl` and `model`, then wraps it into `ChatClient`.

#### Scenario: 配置式构建

- **WHEN** 使用 `ChatGenerationStep.builder().apiKey(...).model(...).build()`
- **THEN** 返回基于 `OpenAiChatModel` + `ChatClient.create` 的 `ChatGenerationStep` 实例
