# 开发计划：ChatGenerationStep 多轮对话记忆

## 概述

将 `ChatGenerationStep` 从裸 `ChatModel.call` 改为基于 `ChatClient` + `MessageChatMemoryAdvisor`
+ `ChatMemory`，支持按 `conversationId` 隔离的多轮对话。JDBC 记忆采用 Spring AI 官方
`JdbcChatMemoryRepository`（`spring-ai-starter-model-chat-memory-repository-jdbc`）。

**模块**：aimi-rag-generation

---

## 1. 依赖

- [x] 1.1 `aimi-rag-generation/pom.xml` 添加 `spring-ai-client-chat`
  - 提供 `ChatClient` / `MessageChatMemoryAdvisor`
- [x] 1.2 `aimi-rag-generation/pom.xml` 添加 `spring-ai-starter-model-chat-memory-repository-jdbc`
  - 提供官方 `JdbcChatMemoryRepository`
- [x] 1.3 移除显式 `spring-jdbc` 依赖（由官方 starter 传递引入）

---

## 2. 核心改造

- [x] 2.1 `ChatGenerationStep` 持有 `ChatClient` + `ChatMemory` + `ConversationIdResolver` 替换 `ChatModel`
  - 新增常量 `CONVERSATION_ID_KEY = "conversation_id"`
  - 新增兜底字段 `defaultConversationId`，`build()` 时自动生成 UUID
  - 默认 `ChatMemory` = `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`，maxMessages=20

- [x] 2.2 重写 `doProcess`
  - 用 `ConversationIdResolver` 解析 conversationId，空则用 `defaultConversationId`
  - `chatClient.prompt().messages(input).advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)).call().content()`

- [x] 2.3 Builder 增加 `chatMemory(...)` / `jdbcChatMemory(JdbcTemplate)`，保留 `builder(ChatModel)`
  - `jdbcChatMemory` 内部用官方 `JdbcChatMemoryRepository` + `MessageWindowChatMemory`

- [x] 2.4 ConfigurableBuilder 增加 `chatMemory(...)` / `jdbcChatMemory(JdbcTemplate)`
  - `jdbcChatMemory` 内部用官方 `JdbcChatMemoryRepository` + `MessageWindowChatMemory`

- [x] 2.5 新增 `ConversationIdResolver`，Builder/ConfigurableBuilder 增加 `conversationIdResolver(...)`

---

## 3. JDBC 记忆（官方 JdbcChatMemoryRepository）

- [x] 3.1 引入官方 `JdbcChatMemoryRepository`（依赖已加）
- [x] 3.2 `jdbcChatMemory(JdbcTemplate)` 组装 `JdbcChatMemoryRepository` + `MessageWindowChatMemory`
- [x] 3.3 删除自写 `JdbcChatMemory`

---

## 4. 测试

- [x] 4.1 多轮：同 `conversationId` 第二次调用携带历史
- [x] 4.2 隔离：不同 `conversationId` 历史互不干扰
- [x] 4.3 context 未提供 `conversation_id` 时用自动生成的兜底 UUID
- [x] 4.4 自定义 `ChatMemory` 生效
- [x] 4.5 向后兼容：`builder(ChatModel)` 仍可构建并生成
- [x] 4.6 `JdbcChatMemoryRepository` 用 HSQLDB 内存库：存储/读取/清空正确
- [x] 4.7 多个 `ChatGenerationStep` 且 context 未提供 `conversation_id` 时历史互不干扰
- [x] 4.8 自定义 `conversationIdResolver` 生效

---

## 5. 验证

- [x] 5.1 编译通过
- [x] 5.2 单测通过（ChatGenerationStepTest + JdbcChatMemoryRepository 测试）
