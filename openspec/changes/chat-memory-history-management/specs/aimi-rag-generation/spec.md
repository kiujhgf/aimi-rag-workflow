## ADDED Requirements

### Requirement: ChatGenerationStep 挂载 MessageChatMemoryAdvisor 实现多轮工作记忆

`ChatGenerationStep` SHALL build its `ChatClient` with a Spring AI `MessageChatMemoryAdvisor` backed by a `ChatMemory`, so that conversation history is automatically prepended to each request and the new exchange is appended after each call.

#### Scenario: 历史自动拼入

- **WHEN** 同一个 `conversationId` 下发起第二次调用
- **THEN** 第二次模型请求中包含第一次调用的历史消息

#### Scenario: 本轮消息自动写回记忆

- **WHEN** 一次调用完成
- **THEN** 本轮 user 与 assistant 消息被写入该 `conversationId` 的 `ChatMemory`

#### Scenario: 不同会话历史隔离

- **WHEN** 两个调用使用不同的 `conversationId`
- **THEN** 两者的历史互不干扰

### Requirement: conversationId 可插拔解析并带非空兜底

`ChatGenerationStep` SHALL resolve the conversation id via a pluggable `ConversationIdResolver` defaulting to reading `conversation_id` from `FlowContext`, and SHALL fall back to a build-time generated UUID when the resolved value is null or empty. The resolved id SHALL be passed to advisors via the `ChatMemory.CONVERSATION_ID` parameter.

#### Scenario: 从上下文读取会话 id

- **WHEN** 默认 resolver 下 `FlowContext` 中 `conversation_id` 为 `"session-A"`
- **THEN** 使用 `"session-A"` 作为会话标识

#### Scenario: 未提供时用自动生成兜底

- **WHEN** 解析结果为 null 或空
- **THEN** 使用构建期自动生成的非空 UUID 作为会话标识，且不抛出异常

#### Scenario: 自定义 resolver 生效

- **WHEN** builder 通过 `conversationIdResolver(...)` 指定自定义解析
- **THEN** 步骤使用该 resolver 解析会话 id

### Requirement: ChatMemory 可插拔

`ChatGenerationStep` SHALL allow a custom `ChatMemory` to be supplied via the builder, defaulting to an in-memory `MessageWindowChatMemory` backed by `InMemoryChatMemoryRepository`, and SHALL allow selecting a JDBC-backed memory via a convenience builder method.

#### Scenario: 默认内存实现

- **WHEN** 未显式提供 `ChatMemory`
- **THEN** 使用 `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`（最大消息数 20）

#### Scenario: 注入自定义实现

- **WHEN** builder 通过 `chatMemory(...)` 传入自定义 `ChatMemory`
- **THEN** 步骤使用该实现而非默认内存实现

#### Scenario: 便捷选择 JDBC 记忆

- **WHEN** 调用 builder 的 `jdbcChatMemory(jdbcTemplate)` 后构建
- **THEN** 步骤使用官方 `JdbcChatMemoryRepository` + `MessageWindowChatMemory` 包装的持久化记忆

### Requirement: CompressingChatMemory 提供基于 LLM 的上下文压缩

The module SHALL provide a `CompressingChatMemory` implementing `ChatMemory` that, when the estimated token count of a conversation exceeds a configured threshold, compresses older messages (all but the most recent N) into an LLM-generated summary and returns `[summary, recentMessages]` from `get(...)`.

#### Scenario: 超阈值时触发压缩

- **WHEN** 某会话消息估算 token 超过 `maxTokens` 阈值
- **THEN** `get(conversationId)` 返回 LLM 生成的摘要消息加上最近 `keepRecentMessages` 条原始消息，而非全量历史

#### Scenario: 未超阈值时原样返回

- **WHEN** 某会话消息估算 token 未超过阈值
- **THEN** `get(conversationId)` 返回 repository 中的全量消息，不调用 LLM

#### Scenario: 摘要缓存命中

- **WHEN** 会话无新消息写入且已有有效摘要缓存
- **THEN** `get(...)` 直接返回缓存摘要，不重复调用 LLM

#### Scenario: 写入新消息使摘要失效

- **WHEN** 调用 `add(...)` 写入新消息
- **THEN** 该会话的摘要缓存被标记失效，下次 `get(...)` 重新评估压缩

#### Scenario: 压缩失败降级

- **WHEN** LLM 摘要调用抛出异常
- **THEN** `get(...)` 降级返回不压缩的窗口消息，不向调用方抛出异常

### Requirement: ChatHistoryRecordingAdvisor 记录全量历史与每轮 token（Call 路径）

The module SHALL provide a `ChatHistoryRecordingAdvisor` that, on the call path, records the user message before the model call and the assistant response plus token usage after the model call into the chat history store.

#### Scenario: 成功调用落库 user 与 assistant

- **WHEN** 一次 call 成功完成
- **THEN** 历史存储中新增该轮的 user 行与 assistant 行，assistant 行携带 `prompt_tokens` / `completion_tokens` / `total_tokens`，状态为 SUCCESS

#### Scenario: token 读不到时降级

- **WHEN** `ChatResponse` 中无 usage 元数据
- **THEN** token 字段写空，历史记录仍正常落库，不抛出异常

### Requirement: ChatHistoryRecordingAdvisor 流式路径可靠落库与补偿

The `ChatHistoryRecordingAdvisor` SHALL implement the streaming path such that chunks are passed through without blocking, the full response is aggregated, and history is persisted on completion with error/cancel compensation.

#### Scenario: 流完成落库

- **WHEN** 流式响应成功完成
- **THEN** 历史存储新增该轮 user/assistant 行，assistant 行携带聚合后的 token 用量，状态为 SUCCESS，且 chunk 透传给下游未被阻塞

#### Scenario: 流异常落库 ERROR

- **WHEN** 流式响应抛出异常
- **THEN** 历史存储新增/更新该轮记录状态为 ERROR，并记录错误信息；不向调用方掩盖原始异常

#### Scenario: 流取消落库 CANCELLED

- **WHEN** 流式响应被下游取消
- **THEN** 历史存储记录该轮状态为 CANCELLED

#### Scenario: 非请求线程仍可落库

- **WHEN** advisor 的 `before`/`after` 回调运行在 memory 专属 `Scheduler`（非请求线程）上
- **THEN** 仍能正确落库，因为 `conversationId` / `executionId` / `model` 通过 advisor context map 显式传递，不依赖 ThreadLocal

### Requirement: Step 输入语义收敛为本轮消息

`ChatGenerationStep` SHALL treat the input `List<Message>` as this-turn messages only; conversation history SHALL be sourced exclusively from the `ChatMemory` via the advisor. Passing full history as input SHALL result in duplication and is therefore discouraged.

#### Scenario: 调用方只传本轮消息

- **WHEN** 调用方在 `messages` 中只放入本轮 user 消息并提供 `conversation_id`
- **THEN** 模型请求 = advisor 拼入的历史 + 本轮消息，无重复

### Requirement: ChatMessageAssembler 仅负责 prompt 组装

`ChatMessageAssembler` SHALL retain only the responsibility of assembling `systemPrompt` and `userPrompt` (with placeholder substitution) when the input `messages` list is empty, and SHALL NOT assemble conversation history.

#### Scenario: messages 非空时透传

- **WHEN** 输入 `messages` 非空
- **THEN** 原样透传，不拼接 system/user prompt，不拼接历史
