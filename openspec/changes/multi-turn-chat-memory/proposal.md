# Proposal: ChatGenerationStep 多轮对话记忆

## Why

当前 `ChatGenerationStep` 是无状态的一次性调用：`doProcess` 拿 `List<Message>` → 调一次
`chatModel.call(new Prompt(input))` → 返回文本。每次调用互不关联，多轮对话需要调用方
自己维护并传入完整历史，导致：

1. 历史维护、去重、长度裁剪的责任全部外抛给调用方，易出错
2. 没有会话（conversation）概念，多会话场景下无法隔离
3. 不符合 Spring AI 的标准多轮对话姿势（`ChatClient` + `ChatMemory`）

## What Changes

- `ChatGenerationStep` 从裸 `ChatModel.call(...)` 改为基于 `ChatClient` 执行
- 引入 `ChatMemory` 会话记忆，通过 `MessageChatMemoryAdvisor` 自动拼接历史
- 引入 `conversationId` 概念，从 `FlowContext` 读取，按会话隔离历史
- 默认使用 `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`（进程内、可裁剪窗口）
- 采用官方 `JdbcChatMemoryRepository`（`spring-ai-starter-model-chat-memory-repository-jdbc`）支持持久化会话记忆
- `ChatMemory` 可插拔：Builder 提供 `chatMemory(...)` 注入自定义实现，并提供 `jdbcChatMemory(...)` 便捷选择 JDBC
- **BREAKING** 输入语义变化：`messages` 从「完整历史」变为「本轮消息」，历史由 advisor 自动带

## Capabilities

### New Capabilities

- `aimi-rag-generation`：`ChatGenerationStep` 支持基于 `ChatClient` + `ChatMemory` 的多轮对话记忆

### Modified Capabilities

- 无（`aimi-rag-core` 的 `GenerationStep` 契约 `List<Message> → String` 保持不变）

## Impact

- **aimi-rag-generation**：`ChatGenerationStep` 改造（`ChatClient` / `ChatMemory` / `conversationId`）
- **依赖**：`spring-ai-client-chat`（`ChatClient` / `MessageChatMemoryAdvisor`）、`spring-ai-starter-model-chat-memory-repository-jdbc`（官方 `JdbcChatMemoryRepository`）
- **调用方**：多轮场景下每轮只传「本轮消息」+ `conversation_id`；单轮用法不受影响
- **测试**：`ChatGenerationStepTest` 需同步调整（mock 对象从 `ChatModel` 变为 `ChatClient` / `ChatMemory`）；新增 `JdbcChatMemoryRepository` 测试
