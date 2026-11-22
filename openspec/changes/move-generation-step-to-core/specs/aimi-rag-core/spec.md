## ADDED Requirements

### Requirement: GenerationStep 位于 core 模块

The aimi-rag-core module SHALL define `GenerationStep` as an abstract step whose input is a `List<Message>` and whose output is a `String` answer.

#### Scenario: 包路径

- **WHEN** 引用 `GenerationStep`
- **THEN** 其全限定名为 `io.github.aimi.rag.workflow.core.step.GenerationStep`

#### Scenario: 输入类型为消息列表

- **WHEN** 查询 `GenerationStep#getInputType()`
- **THEN** 返回 `List.class`

#### Scenario: 输出为答案文本

- **WHEN** `GenerationStep` 的 `doProcess(List<Message>, FlowContext)` 执行成功
- **THEN** 返回模型生成的原始答案文本 `String`

#### Scenario: 步骤元信息

- **WHEN** 查询 `GenerationStep` 的步骤元信息
- **THEN** `getType()` 返回 `StepType.GENERATION`，`getCategory()` 返回 `FlowCategory.QUERY`

### Requirement: 生成步骤职责边界

`GenerationStep` SHALL only invoke the underlying chat model and SHALL NOT assemble prompts or wrap the answer into RAG-specific models.

#### Scenario: 不做 prompt 组装

- **WHEN** 输入消息列表已由调用方准备好
- **THEN** 步骤直接使用该消息列表调用模型，不读取 `retrieval_result` 或 `query_text`

#### Scenario: 不做结果包装

- **WHEN** 模型返回答案文本
- **THEN** 步骤输出原始 `String`，不构造 `QueryResult`

### Requirement: core 提供 Spring AI API 依赖

The aimi-rag-core module SHALL depend on `spring-ai-model` so that `GenerationStep` can reference `org.springframework.ai.chat.messages.Message`.

#### Scenario: 依赖可用

- **WHEN** `aimi-rag-core` 编译 `GenerationStep`
- **THEN** `Message` 类型可从 `spring-ai-model` 解析
