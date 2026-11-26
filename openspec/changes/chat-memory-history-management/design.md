## Context

`aimi-rag-generation` 的 `ChatGenerationStep` 当前用 `ChatClient.prompt().messages(...).call()` 做一次性生成，未挂 advisor / `ChatMemory`；多轮历史、上下文压缩、完整留痕与 token 计量均缺失。`ChatMessageAssembler` 把"组装历史"的职责与 prompt 组装混在一起，语义易混乱。流式模式把冷 `Flux<String>` 写入上下文，订阅时机不可控。

Spring AI 版本 `1.0.0-SNAPSHOT`，可用能力：`MessageChatMemoryAdvisor` / `PromptChatMemoryAdvisor`、`ChatMemory` 接口、`MessageWindowChatMemory`、`InMemoryChatMemoryRepository`、`JdbcChatMemoryRepository`（表 `SPRING_AI_CHAT_MEMORY`）、`LastMaxTokenSizeContentPurger`（仅按 token 截断内容，非 LLM 摘要）、`ChatResponse.getMetadata().getUsage()`（token 用量）。

关键事实（已反编译核对 `MessageChatMemoryAdvisor` 与官方文档）：
- `ChatMemory` 抽象只管理**工作记忆**（发给模型、窗口化、旧消息被裁剪/压缩后丢失），**不管理完整历史**；官方明确完整历史需另存。
- `SPRING_AI_CHAT_MEMORY` 表无 token 列，且非全量，不能用于 token 统计。
- `MessageChatMemoryAdvisor` 同时实现 Call（`before`/`after`）与 Stream（`adviseStream`）：`before` 在 Flux 订阅时、跑在 memory 专属 `Scheduler` 上读历史并立即写 user 消息；`after` 通过 `ChatClientMessageAggregator` 聚合 chunk，**仅在流成功完成时**写 assistant 消息。
- `ChatMemory` API 自 2.1.0 起废弃、3.0.0 移除，未来迁向 Session Memory（`SessionService`）；当前版本仍为现行 API。

`aimi-rag-log` 已有成熟持久化设施：9 种方言抽象（含 SQLite/Oracle/HSQLDB）、`LogSchemaInitializer`、`JdbcTemplate` 仓储，以及 starter 的 DataSource 自动装配。

## Goals / Non-Goals

**Goals:**

- `ChatGenerationStep` 基于 Spring AI 自带 `MessageChatMemoryAdvisor` 实现多轮工作记忆，按 `conversationId` 隔离。
- 提供可插拔 `ChatMemory`：默认内存窗口，可选 JDBC 持久化，新增自研 `CompressingChatMemory` 做 LLM 上下文压缩。
- 提供与工作记忆隔离的**全量历史 + 每轮 token** 存储（`chat_message` 消息表 + `chat_turn_usage` 计量表），通过自研 `ChatHistoryRecordingAdvisor` 落库。
- 流式路径可靠：完成落库、异常/取消有补偿与状态留痕，不阻塞 chunk 透传。
- 自研核心逻辑（压缩策略、历史落库）与 Spring AI 接口解耦，降低未来迁移 Session Memory 的成本。

**Non-Goals:**

- 不实现 agent 循环 / tool calling（`JdbcChatMemoryRepository` 不支持 tool 消息，留待 Session Memory）。
- 不实现多模态消息的 token 精确计量（文本为主，多媒体降级估算或留空）。
- 不做历史表到记忆表的回填工具（仅在风险中记录，后续可选）。
- 不迁移到 Session Memory（当前版本仍用 `ChatMemory`），但为未来迁移预留解耦。
- 不改动 `aimi-rag-core` 的 `GenerationStep` 契约（`List<Message> → String` 不变）。

## Decisions

### 决策 1：advisor 链 = 自带记忆 advisor + 自研历史 advisor

`ChatClient` 构建时按顺序挂载：外层 `ChatHistoryRecordingAdvisor`（自研，负责全量历史 + token + 流式补偿），内层 `MessageChatMemoryAdvisor`（自带，负责工作记忆读拼/写回）。

- 理由：记忆与历史职责分离，自带 advisor 专注工作记忆，自研 advisor 专注审计与计量，互不侵入。
- 备选：只用自带 advisor、把历史塞进 `ChatMemory`——否决，因 `ChatMemory` 窗口化会丢历史且无 token 列。

### 决策 2：记忆与历史物理分表，历史再拆「消息表 + 计量表」

工作记忆走 `SPRING_AI_CHAT_MEMORY`（自带，窗口化），完整历史走自研两张表：`chat_message`（消息级，每行一条）+ `chat_turn_usage`（轮级，每行一次调用，含 token）。三者不共享、不互查。

- 理由：官方明确 `ChatMemory` 不适合存完整历史；分表后历史是权威审计源，记忆是可重建的工作集，允许不一致（记忆丢失可从历史重建）。
- 历史再拆两张表的理由：`total_tokens` 属于「一次调用」而非「一条消息」；若塞进消息行式表，会出现 user 行 token 恒 NULL、且「表名/一行/token」三重语义打架。拆开后 `chat_message` 一行=一条消息（无 token），`chat_turn_usage` 一行=一轮=一次调用（token 自然归属），语义无歧义。

### 决策 3：`CompressingChatMemory` 自研 LLM 压缩

实现 `ChatMemory` 接口，内部持有：委托的 `ChatMemoryRepository`（存全量原始消息）、一个 `ChatModel`/`ChatClient`（生成摘要）、`TokenCountEstimator`、阈值 `maxTokens`、保留最近 N 条 `keepRecentMessages`、摘要缓存（`conversationId → 摘要`，新消息写入即失效）。

- `get(conversationId)`：取全量 → 估 token → 超阈值则把"除最近 N 条外"的旧消息交给 LLM 生成摘要，返回 `[摘要 SystemMessage] + [最近 N 条]`；命中缓存则直接返回。
- `add(...)`：写 repository，标记摘要失效。
- `clear(...)`：清 repository + 缓存。
- 理由：1.0.0 无内置 LLM 压缩，只有 token 截断 purger；用户明确要 LLM 压缩，必须自研。压缩失败降级为不压缩（返回窗口消息），不阻塞主流程。
- 备选：用 `PromptChatMemoryAdvisor` 把历史塞进一个 prompt 变量——否决，它仍不做摘要压缩，且不解决 token 膨胀。

### 决策 4：`conversationId` 可插拔解析 + UUID 兜底

`ConversationIdResolver`（`String resolve(FlowContext)`）默认读 `conversation_id`，空返回 null；Step 持有构建期生成的 `defaultConversationId`（UUID）作为兜底。解析值通过 `ChatMemory.CONVERSATION_ID` 常量写入 advisor context map，传给两个 advisor。

- 理由：满足大多数场景；自定义 resolver 允许复合派生；官方要求 conversationId 非空否则抛异常，故兜底必须保证非空。

### 决策 5：流式可靠性——聚合 + 补偿 + 显式上下文传递

`ChatHistoryRecordingAdvisor` 的 Stream 路径：
- 对 `Flux<ChatClientResponse>` 聚合（复用 `ChatClientMessageAggregator` 或 `reduce` 累积 content + 最终 metadata），`doOnNext` 原样透传不阻塞。
- `doOnComplete`：落库 SUCCESS + 从聚合响应 `getMetadata().getUsage()` 读 token。
- `doOnError` / `doOnCancel`：落库 ERROR / CANCELLED，并可选回滚 memory 中悬空的 user（或标记该轮失败，下轮读取跳过）。
- token 读不到降级为 null/0，不抛异常。
- `before`/`after` 跑在 memory `Scheduler`（非请求线程）：`conversationId`、`executionId`、`model` 等通过 advisor context map 显式传递，自研组件不依赖 ThreadLocal / 请求作用域。
- 理由：解决冷 Flux 未订阅不落库、异常留下悬空 user、请求上下文丢失三类流式问题。

### 决策 6：token 从聚合响应读取，流式需开启 usage

Call 路径直接从 `ChatResponse.getMetadata().getUsage()` 取；Stream 路径从聚合后的最终 `ChatResponse` 取。OpenAI 流式需 `stream_options.include_usage=true` 才会在末尾 chunk 带 usage——通过 `OpenAiChatOptions` 或 advisor 注入确保开启。

### 决策 7：`ChatMessageAssembler` 收窄为 prompt 组装

advisor 接管历史拼接后，Step 输入语义收敛为"本轮消息"。`ChatMessageAssembler` 仅保留 `messages` 为空时拼 `systemPrompt` + `userPrompt` 的职责（含占位符替换），不再承担历史拼接；调用方传 `messages` 时只应传本轮消息。

### 决策 8：历史持久化复用 `aimi-rag-log` 方言体系

在 `JdbcLogRepositoryDialect` 接口扩展 `chat_message` 与 `chat_turn_usage` 的建表/索引/CRUD SQL，`AbstractLogRepositoryDialect` 给默认实现，各方言按需覆盖类型差异；`LogSchemaInitializer` 追加建表语句；新增 `ChatMessageRepository` 与 `ChatTurnUsageRepository`（纯 `JdbcTemplate`，不引入 JPA/MyBatis）。

- 理由：复用已测试的 9 方言覆盖与建表机制，避免重复造轮子；与现有 `flow_execution` 等表一致风格。

## 数据模型

### 表总览

| 表 | 归属 | 是否需我们建 DDL | 用途 |
|----|------|------------------|------|
| `SPRING_AI_CHAT_MEMORY` | Spring AI 官方 `JdbcChatMemoryRepository` | 否（自动建表） | 工作记忆，窗口化，会被裁剪 |
| `chat_message` | 本变更 | 是 | 完整历史，消息级（每行一条），永久保留 |
| `chat_turn_usage` | 本变更 | 是 | 每轮一次调用的计量（token、状态、耗时） |

需新建 **2 张**表：`chat_message`（纯消息，无 token）+ `chat_turn_usage`（轮级计量，token 归属此处）。会话级 token 总量用 `SUM(total_tokens) GROUP BY conversation_id` 对 `chat_turn_usage` 聚合。

**术语与粒度**（消除「一行 vs 一次请求」歧义）：

- `chat_message`：一行 = 一条消息（user/assistant/system）。一轮 = 一次 LLM 调用 = 2 行（`turn_sequence` 相同，`message_index` 区分）。
- `chat_turn_usage`：一行 = 一轮 = 一次 LLM 调用。`total_tokens` 属于这一行，语义与表名完全自洽。
- 两张表都记录该会话的**全部**轮次，永久全量，不随工作记忆窗口裁剪。
- `turn_sequence` 由 repository 在保存时分配（`MAX(turn_sequence)+1`，唯一键冲突时重试）；`id` 用 UUID 主键，序号仅用于逻辑排序。

参考：官方 `SPRING_AI_CHAT_MEMORY`（1.0.0 实际 DDL，已从 jar 中提取，仅作对照，不由我们建）：

```sql
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content        TEXT NOT NULL,
    type           VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"    TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");
```

### chat_message（新建，消息级）

逻辑 DDL（方言据此替换类型）：

```sql
CREATE TABLE IF NOT EXISTS chat_message (
    id              VARCHAR(64)  PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL,
    turn_sequence   INT          NOT NULL,
    message_index   INT          NOT NULL,
    message_role    VARCHAR(16)  NOT NULL,
    content         TEXT,
    created_time    TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_message_conv_turn_idx
    ON chat_message (conversation_id, turn_sequence, message_index);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation
    ON chat_message (conversation_id, turn_sequence, message_index);
```

字段约定：

| 字段 | 说明 |
|------|------|
| `conversation_id` | 会话隔离键，长度 128（官方表仅 36，见风险项） |
| `turn_sequence` | 会话内轮次，从 1 递增 |
| `message_index` | 轮内消息序号：user=0、assistant=1（为未来 tool/system 留位） |
| `message_role` | `user` / `assistant` / `system`；不用 `role` 以规避 MySQL/Oracle 关键字风险 |
| `content` | 消息正文 |
| `created_time` | 消息产生时间 |

### chat_turn_usage（新建，轮级计量）

逻辑 DDL：

```sql
CREATE TABLE IF NOT EXISTS chat_turn_usage (
    id                VARCHAR(64)  PRIMARY KEY,
    conversation_id   VARCHAR(128) NOT NULL,
    turn_sequence     INT          NOT NULL,
    model             VARCHAR(128),
    execution_id      VARCHAR(64),
    status            VARCHAR(32)  NOT NULL,
    error_message     TEXT,
    prompt_tokens     INT,
    completion_tokens INT,
    total_tokens      INT,
    start_time        TIMESTAMP    NOT NULL,
    end_time          TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_turn_usage_conv_turn
    ON chat_turn_usage (conversation_id, turn_sequence);
CREATE INDEX IF NOT EXISTS idx_chat_turn_usage_execution
    ON chat_turn_usage (execution_id);
```

字段约定：

| 字段 | 说明 |
|------|------|
| `conversation_id` + `turn_sequence` | 唯一定位一轮 |
| `model` | 本次调用的模型名 |
| `execution_id` | 关联 `flow_execution.id`，可空，用于链路追踪 |
| `status` | `SUCCESS` / `ERROR` / `CANCELLED`，本轮调用的最终状态 |
| `error_message` | 失败原因，可空 |
| `prompt_tokens` / `completion_tokens` / `total_tokens` | 本次调用的 token 用量，一次一行，无歧义 |
| `start_time` / `end_time` | 本轮调用起止时间 |

设计要点：

- **消息与计量分离**：`chat_message` 只存「谁在什么时候说了什么」；`chat_turn_usage` 只存「这一轮花了多少 token、成功与否」。token 不再出现在消息行，消除语义噪音。
- **唯一索引做幂等**：`chat_message` 用 `(conversation_id, turn_sequence, message_index)` 唯一；`chat_turn_usage` 用 `(conversation_id, turn_sequence)` 唯一。流式重试/重复落库不产生重复行，repository 用 upsert 或捕获重复键后更新。
- **轮级状态单一来源**：`status` / `error_message` 只放 `chat_turn_usage`，`markError` / `markCancelled` 只更新这一行，不再有「两行状态不同步」的问题。
- **失败轮的消息**：若调用失败，`chat_message` 仍记录已产生的 user 消息（`before` 阶段写入），assistant 消息缺失由 usage 表的 `status=ERROR` 表达，不强制补空 assistant 行。

方言类型映射（由 `JdbcLogRepositoryDialect` 输出，复用现有 `varcharType` / `timestampType` / `textType`）：

| 逻辑类型 | PostgreSQL | MySQL | SQL Server | Oracle | HSQLDB/H2 | SQLite |
|----------|-----------|-------|-----------|--------|----------|--------|
| VARCHAR | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR2(n)` | `VARCHAR(n)` | `VARCHAR(n)` |
| TEXT | `TEXT` | `TEXT` | `NVARCHAR(MAX)` | `CLOB` | `CLOB` | `TEXT` |
| TIMESTAMP | `TIMESTAMP` | `DATETIME` | `DATETIME2` | `TIMESTAMP` | `TIMESTAMP` | `TEXT`（ISO-8601） |
| INT | `INT` | `INT` | `INT` | `NUMBER(10)` | `INT` | `INTEGER` |

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| Spring AI 1.0.0-SNAPSHOT 为快照版，API 可能变动 | 仅依赖稳定接口（`ChatMemory` / `MessageChatMemoryAdvisor` / `ChatResponse.metadata`），关键类已反编译核对；升级时回归 |
| 流式 `after` 在 memory Scheduler 执行，拿不到请求上下文 | conversationId/executionId/model 通过 advisor context map 显式传递；自研组件不依赖 ThreadLocal |
| 流式异常留下悬空 user（memory 与 history 不一致） | `ChatHistoryRecordingAdvisor` 的 `doOnError/doOnCancel` 落 ERROR/CANCELLED；可选回滚或标记失败轮，下轮读取跳过 |
| LLM 压缩本身消耗额外 token、增加延迟 | 阈值设大（如 ≥8k 才触发）；摘要缓存避免重复压缩；压缩失败降级不压缩 |
| `chat_message` / `chat_turn_usage` 与 `SPRING_AI_CHAT_MEMORY` 双写一致性 | 三者独立：历史两表为权威全量审计，memory 表为可重建工作集；允许不一致，后续可选提供回填工具 |
| `ChatMemory` API 未来废弃（2.1.0→3.0.0 迁 Session Memory） | 自研 `CompressingChatMemory` 与 `ChatHistoryRecordingAdvisor` 的核心逻辑与 Spring AI 接口解耦，仅适配层需改 |
| `JdbcChatMemoryRepository` 不支持 tool call 消息 | 当前不启用 tool calling；后续启用时改用 Session Memory 或自研 repository |
| 流式 usage 未开启导致 token 读不到 | 确保 `stream_options.include_usage=true`；读不到降级为空，不阻塞主流程 |
| 官方 `SPRING_AI_CHAT_MEMORY.conversation_id` 仅 `VARCHAR(36)`，自定义会话 id（如 `user:session`）可能超长导致 JDBC 记忆写入失败 | `ConversationIdResolver` 默认使用 UUID（36 位）；文档明确要求自定义 id 不超过 36；`chat_message`/`chat_turn_usage` 独立用 `VARCHAR(128)` 不受影响 |

## Migration Plan

1. `aimi-rag-log` 先落 `chat_message` + `chat_turn_usage` 表及 `ChatMessageRepository` + `ChatTurnUsageRepository` + 方言扩展 + 建表，单测验证（不影响现有表）。
2. `aimi-rag-generation` 改造 `ChatGenerationStep` 挂 advisor + conversationId + pluggable memory，单测多轮/隔离/兜底。
3. 新增 `CompressingChatMemory`，单测压缩触发/缓存/降级。
4. 新增 `ChatHistoryRecordingAdvisor`，单测 call 落库、stream 完成/异常/取消。
5. 联调：多轮 call/stream、异常取消、token 统计、压缩生效、跨会话隔离。
6. 回滚：新增表与新类均为增量，关闭配置开关（`history.enabled=false`、不挂压缩 memory）即可回退到无历史/无压缩行为；两张新表保留不影响主流程。

## Open Questions

- 历史粒度：**已定——`chat_message` 行式消息 + `chat_turn_usage` 轮级计量两张表**（见数据模型），token 只落在 usage 表，消除歧义。
- token 计量是否关联 `flow_execution.execution_id`？**已定：加 nullable `execution_id` 列**（在 `chat_turn_usage`），便于链路追踪但不强依赖。
- 压缩摘要消息用 `SystemMessage` 还是自定义 role？倾向 `SystemMessage`（`MessageWindowChatMemory` 裁剪时保留 SystemMessage，摘要不被误裁）。
- 是否本期就提供"从 `chat_message` 回填 `ChatMemoryRepository`"的工具？倾向后续可选，本期不做。
