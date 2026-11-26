## Why

当前 `ChatGenerationStep` 虽已基于 `ChatClient`，但未挂载任何 advisor / `ChatMemory`，多轮对话、上下文压缩、完整历史留痕与 token 计量全部缺失或外抛给调用方。Spring AI 1.0.0 自带的 `MessageChatMemoryAdvisor` + `ChatMemory` 只解决"给模型的工作记忆"，且是窗口化的——旧消息会被裁剪丢弃，既不保留完整历史，也不记录每轮 token 消耗；流式场景下其 `before`/`after` 跑在 memory 专属 `Scheduler` 上、冷 `Flux` 未订阅完成就不落库，容易出现记忆缺失或"只有 user 没有 assistant"的悬空记录。需要一套基于 Spring AI 自带 advisor、同时补齐压缩 / 全量历史 / token / 流式可靠性的记忆与历史管理能力。

> 本变更覆盖并取代先前未落地的 `multi-turn-chat-memory` 变更（其 `ChatClient` + `ChatMemory` 基础并入本次设计，在此之上扩展压缩、历史表、token 与流式修复）。

## What Changes

- `ChatGenerationStep` 构建 `ChatClient` 时挂载自带 `MessageChatMemoryAdvisor`（工作记忆），并按配置挂载自研 `ChatHistoryRecordingAdvisor`（全量历史 + token）。
- 引入可插拔 `ConversationIdResolver`（默认读 `FlowContext` 的 `conversation_id`，空则用构建期 UUID 兜底），通过 `ChatMemory.CONVERSATION_ID` 参数传给 advisor。
- `ChatMemory` 可插拔：默认 `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`；可选 `JdbcChatMemoryRepository` 持久化；新增自研 `CompressingChatMemory` 提供基于 LLM 的上下文压缩。
- 新增独立的 `chat_message`（消息级）与 `chat_turn_usage`（轮级计量）两张表及 `ChatMessageRepository` / `ChatTurnUsageRepository`（位于 `aimi-rag-log`，复用现有方言体系），分别全量保留每轮 user/assistant 消息与 `prompt_tokens` / `completion_tokens` / `total_tokens`，与工作记忆表完全隔离。
- `ChatHistoryRecordingAdvisor` 同时实现 Call 与 Stream 路径：流式下聚合 chunk、完成时落库，异常/取消落 ERROR/CANCELLED 并补偿悬空记录；token 从聚合后 `ChatResponse.getMetadata().getUsage()` 读取，读不到降级不阻塞。
- **BREAKING** Step 输入语义收敛为「本轮消息」：历史由 advisor 自动拼接，调用方不应再传入完整历史（否则会与 advisor 拼接的记忆重复）。
- `ChatMessageAssembler` 仅保留 system/user prompt 组装职责，不再承担历史拼接。

## Capabilities

### New Capabilities

- `aimi-rag-generation`：`ChatGenerationStep` 接入 Spring AI advisor 链实现多轮记忆；可插拔 `ConversationIdResolver` 与 `ChatMemory`；提供自研 `CompressingChatMemory`（LLM 上下文压缩）与 `ChatHistoryRecordingAdvisor`（历史 + token 记录，含流式补偿）。
- `chat-history-persistence`：在 `aimi-rag-log` 中新增 `chat_message` 与 `chat_turn_usage` 表及 `ChatMessageRepository` / `ChatTurnUsageRepository`（纯 JdbcTemplate、复用现有方言），分别全量存储对话消息与每轮 token 用量，与工作记忆隔离，支持按会话查询与状态更新。

### Modified Capabilities

无既有主 spec 需要增量修改（`openspec/specs/` 尚未建立）。

## Impact

- **aimi-rag-generation**：`ChatGenerationStep` 改造（advisor 链、conversationId、pluggable memory/history）；新增 `CompressingChatMemory`、`ChatHistoryRecordingAdvisor`、`ConversationIdResolver`；`pom.xml` 新增 `spring-ai-starter-model-chat-memory-repository-jdbc`（可选）。
- **aimi-rag-log**：`JdbcLogRepositoryDialect` 接口与各方言扩展 `chat_message` / `chat_turn_usage` 相关 SQL；`LogSchemaInitializer` 追加建表/索引；新增 `ChatMessageRecord` / `ChatTurnUsageRecord` / `ChatMessageRepository` / `ChatTurnUsageRepository`。
- **调用方**：多轮场景每轮只传「本轮消息」+ `conversation_id`；单轮无 `conversation_id` 用法不受影响（兜底 UUID）。
- **依赖**：Spring AI `ChatMemory` API 自 2.1.0 起废弃、3.0.0 移除，未来需迁移到 Session Memory——自研组件核心逻辑与 Spring AI 接口解耦以降低迁移成本。
- **测试**：`ChatGenerationStepTest` 同步调整；新增压缩、历史落库、流式异常/取消补偿等单测。
