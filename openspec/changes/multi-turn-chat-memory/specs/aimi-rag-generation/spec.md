## ADDED Requirements

### Requirement: ChatGenerationStep 基于 ChatClient

The aimi-rag-generation module SHALL have `ChatGenerationStep` execute via Spring AI `ChatClient` instead of a raw `ChatModel.call(...)`.

#### Scenario: 使用 ChatClient 调用

- **WHEN** `ChatGenerationStep.doProcess` 收到本轮消息列表
- **THEN** 通过 `ChatClient.prompt().messages(...).call()` 生成回答，而非 `chatModel.call(...)`

### Requirement: 多轮对话记忆

`ChatGenerationStep` SHALL attach a `MessageChatMemoryAdvisor` backed by a `ChatMemory`, so conversation history is automatically prepended on each call and appended after each call.

#### Scenario: 历史自动拼接

- **WHEN** 同一个 `conversationId` 下发起第二次调用
- **THEN** 模型请求中包含第一次调用的历史消息

#### Scenario: 历史自动写回

- **WHEN** 一次调用完成
- **THEN** 本轮消息被写入 `ChatMemory`

### Requirement: conversationId 可插拔解析，自动生成兜底

`ChatGenerationStep` SHALL resolve the conversation id via a pluggable `ConversationIdResolver` — defaulting to reading `conversation_id` from `FlowContext` — and fall back to a build-time UUID when the resolved value is empty. The resolved id is passed to the advisor via the `chat_memory_conversation_id` parameter.

#### Scenario: 从上下文获取会话 id

- **WHEN** 默认 resolver 下 `FlowContext` 中 `conversation_id` 为 `"session-A"`
- **THEN** 使用 `"session-A"` 作为会话标识

#### Scenario: 自定义解析

- **WHEN** builder 通过 `conversationIdResolver(...)` 指定自定义解析
- **THEN** 步骤使用该 resolver 解析会话 id，而非固定读取 `conversation_id`

#### Scenario: 未提供则用自动生成

- **WHEN** 解析结果为 null 或空
- **THEN** 使用构建期自动生成的 UUID 作为会话标识

#### Scenario: 多个步骤历史隔离

- **WHEN** 多个 `ChatGenerationStep` 且解析结果均为空
- **THEN** 各步骤使用各自自动生成的 UUID，历史互不干扰

#### Scenario: 复用同一会话

- **WHEN** 两个 `ChatGenerationStep` 解析出相同的 `conversationId`
- **THEN** 它们共享同一会话历史

### Requirement: ChatMemory 可插拔

`ChatGenerationStep` SHALL allow a custom `ChatMemory` to be supplied via the builder, defaulting to an in-memory `MessageWindowChatMemory` backed by `InMemoryChatMemoryRepository`.

#### Scenario: 默认内存实现

- **WHEN** 未显式提供 `ChatMemory`
- **THEN** 使用 `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`（最大消息数 20）

#### Scenario: 注入自定义实现

- **WHEN** builder 通过 `chatMemory(...)` 传入自定义 `ChatMemory`
- **THEN** 步骤使用该实现而非默认内存实现

### Requirement: 基于官方 JdbcChatMemoryRepository 的 JDBC 记忆

The aimi-rag-generation module SHALL use Spring AI's official `JdbcChatMemoryRepository` (from `spring-ai-starter-model-chat-memory-repository-jdbc`) combined with `MessageWindowChatMemory`, instead of a hand-written implementation.

#### Scenario: 持久化存储

- **WHEN** 使用 `jdbcChatMemory(JdbcTemplate)` 构建步骤
- **THEN** 对话记忆由官方 `JdbcChatMemoryRepository` 持久化到数据库

#### Scenario: 不依赖 JPA/MyBatis

- **WHEN** 使用 JDBC 记忆
- **THEN** 仅依赖官方 starter（基于 spring-jdbc），不引入 JPA 或 MyBatis

### Requirement: 创建时可选 JDBC 或自定义 ChatMemory

`ChatGenerationStep` SHALL allow selecting JDBC memory at construction time via `jdbcChatMemory(JdbcTemplate)`, and SHALL allow injecting any custom `ChatMemory` via `chatMemory(...)`.

#### Scenario: 便捷选择 JDBC

- **WHEN** 调用 `builder.jdbcChatMemory(jdbcTemplate).build()`
- **THEN** 步骤使用官方 `JdbcChatMemoryRepository` + `MessageWindowChatMemory` 包装的记忆

#### Scenario: 传入自定义实现

- **WHEN** 调用 `builder.chatMemory(customMemory).build()`
- **THEN** 步骤使用该 `customMemory`

### Requirement: 向后兼容的构建入口

`ChatGenerationStep` SHALL retain `builder(ChatModel)` and `builder()` entry points, wrapping the model in a `ChatClient` internally.

#### Scenario: builder(ChatModel) 仍可用

- **WHEN** 调用 `ChatGenerationStep.builder(chatModel).build()`
- **THEN** 返回基于该 `ChatModel` 构建 `ChatClient` 的步骤实例

#### Scenario: ConfigurableBuilder 仍可用

- **WHEN** 调用 `ChatGenerationStep.builder().apiKey(...).model(...).build()`
- **THEN** 返回基于 `OpenAiChatModel` 构建 `ChatClient` 的步骤实例
