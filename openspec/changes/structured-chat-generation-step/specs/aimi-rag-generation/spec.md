## ADDED Requirements

### Requirement: 聊天步骤统一使用 ChatClient

Both `ChatGenerationStep` and `StructuredChatGenerationStep<T>` SHALL invoke the model through Spring AI `ChatClient`
(`ChatClient.prompt().messages(...)`), instead of calling `ChatModel.call(Prompt)` directly.

#### Scenario: 文本生成（非流式）

- **WHEN** `ChatGenerationStep` 未开启 `streaming`
- **THEN** 通过 `chatClient.prompt().messages(messages).call().content()` 获取文本结果并写入上下文

#### Scenario: 文本生成（流式，写入 Flux）

- **WHEN** `ChatGenerationStep` 通过 builder 设置 `streaming(true)`
- **THEN** 不发起模型请求，仅把 `chatClient.prompt().messages(messages).stream().content()` 返回的冷
  `Flux<String>` 写入 context key `stream`，由下游节点自行订阅；该模式下不写入默认的 `answer`

#### Scenario: 配置式构建

- **WHEN** 使用 `builder()` 配置式入口构建任一 Step
- **THEN** 内部用 `OpenAiChatModel` 包装为 `ChatClient` 后注入

### Requirement: 结构化输出步骤

The aimi-rag-generation module SHALL provide `StructuredChatGenerationStep<T>` that converts model output into a
caller-specified type. The step SHALL **always** use the streaming API
(`ChatClient...stream().content()`), wait for all chunks to arrive, and then convert the joined text via
`StructuredOutputConverter.convert(...)`.

#### Scenario: DTO 输出

- **WHEN** 使用 `builder(chatClient, Person.class)` 构建，模型流式返回完整 JSON `{"name":"张三","age":30}`
- **THEN** 步骤阻塞等待流结束后调用 converter，输出 `Person` 实例，字段值与 JSON 一致

#### Scenario: 泛型输出

- **WHEN** 使用 `builder(chatClient, new ParameterizedTypeReference<List<Person>>() {})` 构建，模型返回 JSON 数组
- **THEN** 步骤输出 `List<Person>`

#### Scenario: 自定义 converter

- **WHEN** 使用 `builder(chatClient, customStructuredOutputConverter)` 构建
- **THEN** 步骤在流式请求前把 `customStructuredOutputConverter.getFormat()` 追加到最后一条 `UserMessage`，
  流结束后调用其 `convert(joinedText)` 完成转换

### Requirement: 消息组装与透传

The step SHALL resolve messages as follows: pass `messages` through unchanged when non-empty; otherwise assemble
`SystemMessage(systemPrompt)` followed by `UserMessage(userPrompt)`; both support `{key}` placeholders resolved from
`FlowContext`.

#### Scenario: messages 透传

- **WHEN** `FlowContext` 的 `messages` 非空
- **THEN** 原消息原样传给 ChatClient，调用方传入的列表不被修改

#### Scenario: prompt 组装

- **WHEN** `messages` 为空且配置了 `systemPrompt` / `userPrompt`
- **THEN** 消息顺序为 `SystemMessage(systemPrompt)`、`UserMessage(userPrompt)`

#### Scenario: 格式指令

- **WHEN** `StructuredChatGenerationStep` 执行流式请求
- **THEN** 步骤把 `converter.getFormat()` 追加到最后一条 `UserMessage`（与 ChatClient 非流式行为一致）；
  若消息列表中不存在 `UserMessage`，则追加一条仅含格式指令的 `UserMessage`

### Requirement: 默认输出映射

The step SHALL write the converted result to the `structured` context key by default and SHALL allow a custom
`OutputResolver<T>`.

#### Scenario: 默认写入 structured

- **WHEN** 转换成功
- **THEN** `FlowContext` 的 `structured` key 为转换后的对象

#### Scenario: 自定义输出 resolver

- **WHEN** builder 设置了自定义 `OutputResolver<T>`
- **THEN** 结果交给该 resolver 处理，不再写入 `structured`

### Requirement: 失败行为

The step SHALL fail with `StepExecuteException` when there is no input at all or when the model output cannot be
converted.

#### Scenario: 三者皆空

- **WHEN** `messages` 为空且未配置 `systemPrompt` 与 `userPrompt`
- **THEN** 抛出 `StepExecuteException`，不调用模型

#### Scenario: 输出不可转换

- **WHEN** 模型返回的文本无法转换为目标类型
- **THEN** 抛出 `StepExecuteException`

### Requirement: 步骤元数据

The step SHALL expose name `structured-chat-generation`, type `GENERATION` and category `QUERY`.

#### Scenario: 元数据

- **WHEN** 读取 `getName()` / `getType()` / `getCategory()`
- **THEN** 分别为 `structured-chat-generation` / `StepType.GENERATION` / `FlowCategory.QUERY`
