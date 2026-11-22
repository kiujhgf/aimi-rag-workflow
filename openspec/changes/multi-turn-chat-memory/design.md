# 设计文档：ChatGenerationStep 多轮对话记忆

## Context

`ChatGenerationStep` 当前用 `ChatModel.call(new Prompt(input))` 做一次性生成，无历史记忆。
Spring AI 1.0.0-SNAPSHOT 已提供标准多轮对话能力：

- `ChatClient`：`ChatClient.builder(chatModel)` 构建的对话入口，支持 advisor 链
- `MessageChatMemoryAdvisor`：在调用前从 `ChatMemory` 读历史拼进请求、调用后把新消息写回
- `ChatMemory`：按 `conversationId` 存/取/清空消息的接口
- `MessageWindowChatMemory`：`ChatMemory` 的内存实现，支持最大消息数窗口裁剪
- `InMemoryChatMemoryRepository`：`ChatMemoryRepository` 的内存存储

关键常量（已确认）：

- `ChatMemory.CONVERSATION_ID = "chat_memory_conversation_id"`（advisor 参数 key）
- `ChatMemory.DEFAULT_CONVERSATION_ID = "default"`

## Goals / Non-Goals

**Goals:**

- `ChatGenerationStep` 基于 `ChatClient` + `MessageChatMemoryAdvisor` 实现多轮对话
- 通过 `conversationId` 隔离会话
- `ChatMemory` 可插拔，默认内存实现
- 提供基于 `JdbcTemplate` 的 `JdbcChatMemory`，支持持久化会话记忆，不依赖 JPA/MyBatis

**Non-Goals:**

- 不改动 `aimi-rag-core` 的 `GenerationStep` 契约（仍为 `List<Message> → String`）
- 不实现 agent 循环 / tool calling
- 不实现 JPA/MyBatis 版本的 `ChatMemory`（仅纯 JDBC）

## Decisions

### 决策 1：ChatClient 替换 ChatModel.call

`ChatGenerationStep` 内部从持有 `ChatModel` 改为持有 `ChatClient`。

- 理由：多轮记忆依赖 advisor 链，只有 `ChatClient` 支持；`ChatModel.call` 无法挂 advisor。
- 备选：手动在 `doProcess` 里拼历史再 `call` —— 重复造 `MessageChatMemoryAdvisor` 的轮子，否决。

### 决策 2：conversationId 可插拔解析，默认从 context 读取，自动生成兜底

`ChatGenerationStep` 通过可插拔的 `ConversationIdResolver` 解析 conversationId，默认实现从
`FlowContext` 读 `conversation_id`（常量 `CONVERSATION_ID_KEY = "conversation_id"`）；解析结果
为空时回退到构建期自动生成的 UUID。

- 默认 resolver：从 `conversation_id` key 读取
- 用户可自定义 resolver（Builder 提供 `conversationIdResolver(...)`）
- 解析结果为空 → 用构建期自动生成的兜底 UUID

- 理由：默认 key 满足大多数场景；自定义 resolver 允许用户从不同 key 或复合逻辑派生会话 id，
  未提供时自动生成独立 UUID 保证「一个 Flow 多个 ChatGenerationStep」默认互不串话。

### 决策 3：默认 ChatMemory = MessageWindowChatMemory + InMemoryChatMemoryRepository

```java
ChatMemory defaultChatMemory() {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(20)
            .build();
}
```

- 理由：进程内即可、支持窗口裁剪防 token 膨胀；`MessageWindowChatMemory` 实现 `ChatMemory` 接口。

### 决策 4：ChatMemory 可插拔

`Builder` / `ConfigurableBuilder` 新增 `chatMemory(ChatMemory)` 配置项，缺省用决策 3 的默认实现。
`ChatClient` 构建时挂 `MessageChatMemoryAdvisor.builder(chatMemory).build()`。

同时提供便捷入口 `jdbcChatMemory(JdbcTemplate)`，内部 `new JdbcChatMemory(jdbcTemplate)` 后走
`chatMemory(...)`。这样创建时既可「选 JDBC」也可「传自定义 ChatMemory」。

### 决策 5：向后兼容

- 保留 `builder(ChatModel chatModel)`：内部 `ChatClient.builder(chatModel).build()`。
- `ConfigurableBuilder` 保留 `apiKey`/`baseUrl`/`model`，构建 `OpenAiChatModel` 后再包 `ChatClient`。

### 决策 6：输入语义 = 本轮消息

`GenerationStep` 契约 `List<Message> → String` 不变，但语义从「完整历史」收敛为「本轮消息」。
调用方每轮只传本轮用户消息，历史由 `MessageChatMemoryAdvisor` 自动拼接并写回。

### 决策 7：使用官方 JdbcChatMemoryRepository

采用 Spring AI 官方的 `JdbcChatMemoryRepository`（来自
`spring-ai-starter-model-chat-memory-repository-jdbc`），它实现 `ChatMemoryRepository`，
配合 `MessageWindowChatMemory` 包装成 `ChatMemory`。不再自写 `JdbcChatMemory`。

- 分层：`JdbcChatMemoryRepository`（存储消息）+ `MessageWindowChatMemory`（窗口裁剪策略）
- 依赖：`spring-ai-starter-model-chat-memory-repository-jdbc`（传递引入 spring-jdbc）
- 多数据库：PostgreSQL / MySQL / SQL Server / HSQLDB 开箱即用（dialect 抽象），自动建表
- `jdbcChatMemory(JdbcTemplate)` 便捷入口内部组装上述两者

## 数据流

```
构建期：
    ChatGenerationStep.builder(model)
        .chatMemory(...)                // 可选；省略则用内存默认实现
        .build()                        // 自动生成默认 UUID 作为兜底 conversationId

运行期：
    FlowContext.set("messages", [本轮UserMessage])
    FlowContext.set("conversation_id", "会话A")   // 可选；省略则用构建期兜底 UUID

ChatGenerationStep.doProcess(input, context):
    1. conversationId = context.get("conversation_id") 非空 ? 该值 : 兜底 UUID
    2. chatClient.prompt()
            .messages(input)
            .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
            .call()
            .content()
    3. MessageChatMemoryAdvisor: before 读历史拼入，after 写回新消息
```

## Risks / Trade-offs

| 风险 / 权衡 | 缓解措施 |
|------|---------|
| `spring-ai-client-chat` / `spring-ai-starter-model-chat-memory-repository-jdbc` 依赖需显式声明 | 在 `aimi-rag-generation/pom.xml` 添加 |
| 内存 ChatMemory 跨请求/重启丢失 | 默认定位为进程内；需持久化时切换到 `jdbcChatMemory(...)` |
| 官方 `JdbcChatMemoryRepository` 的表名/方言差异 | 交由 Spring AI 的 dialect 抽象与自动建表处理 |
| 输入语义变化（完整历史 → 本轮消息）属 BREAKING | 单轮调用方不受影响；多轮调用方需改用 `conversation_id` |
| advisor 参数 key 依赖 Spring AI 内部约定 | 使用 `ChatMemory.CONVERSATION_ID` 常量而非硬编码字符串 |
| 测试 mock 复杂度上升 | mock `ChatClient`/`ChatMemory` 而非 `ChatModel`，或用真实 `InMemoryChatMemoryRepository`；`JdbcChatMemoryRepository` 用 H2 内存库测试 |

## Migration Plan

1. `aimi-rag-generation/pom.xml` 添加 `spring-ai-client-chat`、`spring-ai-starter-model-chat-memory-repository-jdbc`
2. `ChatGenerationStep` 改造：持有 `ChatClient` + `ChatMemory`，重写 `doProcess`
3. Builder/ConfigurableBuilder 增加 `chatMemory(...)` / `jdbcChatMemory(...)`，保留 `builder(ChatModel)`
4. `jdbcChatMemory(JdbcTemplate)` 内部组装官方 `JdbcChatMemoryRepository` + `MessageWindowChatMemory`
5. 更新 `ChatGenerationStepTest`，新增 `JdbcChatMemoryRepository` 测试
6. 编译 + 单测验证

## Open Questions

- 无（JDBC `ChatMemory` 已纳入本次变更；是否进一步支持自动建表开关可后续再定）
