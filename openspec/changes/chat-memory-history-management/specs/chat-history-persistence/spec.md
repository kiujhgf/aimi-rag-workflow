## ADDED Requirements

### Requirement: chat_message 与 chat_turn_usage 表由 schema 初始化器创建

`LogSchemaInitializer` SHALL create a `chat_message` table and a `chat_turn_usage` table (and their indexes) on startup, using the resolved dialect, alongside the existing `flow_execution` / `step_execution` / `step_failure` tables.

#### Scenario: 启动时建表

- **WHEN** 应用启动且 DataSource 可用
- **THEN** `chat_message` 与 `chat_turn_usage` 表及其索引被创建（已存在则跳过）

#### Scenario: chat_message 表结构

- **WHEN** 查询 `chat_message` 表结构
- **THEN** 包含列：`id`、`conversation_id`（VARCHAR(128)）、`turn_sequence`、`message_index`、`message_role`、`content`、`created_time`

#### Scenario: chat_turn_usage 表结构

- **WHEN** 查询 `chat_turn_usage` 表结构
- **THEN** 包含列：`id`、`conversation_id`（VARCHAR(128)）、`turn_sequence`、`model`、`execution_id`（可空）、`status`、`error_message`（可空）、`prompt_tokens`、`completion_tokens`、`total_tokens`、`start_time`、`end_time`（可空）

#### Scenario: 消息表唯一索引

- **WHEN** 对 `chat_message` 检查索引
- **THEN** 存在 `(conversation_id, turn_sequence, message_index)` 唯一索引

#### Scenario: 计量表唯一索引

- **WHEN** 对 `chat_turn_usage` 检查索引
- **THEN** 存在 `(conversation_id, turn_sequence)` 唯一索引

### Requirement: ChatMessageRepository 保存消息行

The module SHALL provide a `ChatMessageRepository` (backed by `JdbcTemplate`, no JPA/MyBatis) that persists messages as rows (`message_role=user`, `message_index=0`; `message_role=assistant`, `message_index=1`), carrying no token fields.

#### Scenario: 保存成功轮次的两条消息

- **WHEN** 调用 `saveMessages` 传入会话 id、轮次序号、user 内容与 assistant 内容
- **THEN** `chat_message` 表新增两行（user 与 assistant），无任何 token 字段

#### Scenario: 重复落库幂等

- **WHEN** 同一 `(conversation_id, turn_sequence, message_index)` 被重复保存（如流式重试）
- **THEN** 不产生重复行，已有行被更新而非报错

### Requirement: ChatTurnUsageRepository 保存每轮 token 用量与状态

The module SHALL provide a `ChatTurnUsageRepository` (backed by `JdbcTemplate`, no JPA/MyBatis) that persists one row per LLM call, carrying `prompt_tokens` / `completion_tokens` / `total_tokens`, `status`, `model`, `execution_id`, and timing.

#### Scenario: 保存成功轮次的用量

- **WHEN** 调用 `saveUsage` 传入会话 id、轮次序号、token 用量、模型与状态 SUCCESS
- **THEN** `chat_turn_usage` 表新增一行，`total_tokens` 等被填充，`status=SUCCESS`

#### Scenario: 一行表示一次调用

- **WHEN** 一次 LLM 调用完成
- **THEN** `chat_turn_usage` 仅新增一行（`conversation_id` + `turn_sequence` 唯一），token 归属该行，与 `chat_message` 的多条消息行区分

#### Scenario: 重复落库幂等

- **WHEN** 同一 `(conversation_id, turn_sequence)` 被重复保存
- **THEN** 不产生重复行，已有行被更新而非报错

### Requirement: 按会话查询全量有序历史

`ChatMessageRepository` SHALL return the full ordered history for a conversation, ascending by `turn_sequence` then `message_index`, independent of any working-memory window.

#### Scenario: 查询返回全量

- **WHEN** 调用 `findByConversationId(conversationId)`
- **THEN** 返回该会话所有消息（含已被工作记忆窗口裁剪掉的旧消息），按轮次与消息序升序排列

#### Scenario: 历史不随工作记忆裁剪丢失

- **WHEN** 工作记忆 `ChatMemory` 因窗口裁剪丢弃了旧消息
- **THEN** `chat_message` 表中的对应旧消息仍然保留，查询结果不受影响

### Requirement: 状态更新支持异常与取消

`ChatTurnUsageRepository` SHALL support updating a turn's status to ERROR or CANCELLED (with optional error message) for streaming failure/cancel compensation, updating the single usage row.

#### Scenario: 标记 ERROR

- **WHEN** 调用 `markError` 传入 `(conversation_id, turn_sequence)` 与错误信息
- **THEN** `chat_turn_usage` 中该行 `status` 更新为 ERROR，`error_message` 更新，`end_time` 被记录

#### Scenario: 标记 CANCELLED

- **WHEN** 调用 `markCancelled` 传入 `(conversation_id, turn_sequence)`
- **THEN** `chat_turn_usage` 中该行 `status` 更新为 CANCELLED

### Requirement: 与工作记忆表物理隔离

The `chat_message` and `chat_turn_usage` stores SHALL be physically separate from Spring AI's working-memory table (`SPRING_AI_CHAT_MEMORY`); they SHALL NOT share tables or read each other's data.

#### Scenario: 相互独立

- **WHEN** 写入或裁剪 `SPRING_AI_CHAT_MEMORY`
- **THEN** `chat_message` 与 `chat_turn_usage` 表数据不受影响，反之亦然

### Requirement: 复用现有方言体系覆盖多数据库

The `chat_message` and `chat_turn_usage` SQL SHALL be produced through the existing `JdbcLogRepositoryDialect` abstraction with a default implementation in `AbstractLogRepositoryDialect`, so that all already-supported databases (PostgreSQL, MySQL, SQL Server, Oracle, HSQLDB, H2, SQLite, etc.) work without per-database hand-written DDL beyond type overrides.

#### Scenario: 方言解析生成正确 SQL

- **WHEN** 为任一已支持数据库解析方言
- **THEN** `chat_message` 与 `chat_turn_usage` 的建表与 CRUD SQL 使用该数据库兼容的类型与语法

### Requirement: 不引入 JPA 或 MyBatis 依赖

The chat history persistence SHALL be implemented with `JdbcTemplate` only and SHALL NOT introduce JPA or MyBatis dependencies, consistent with the existing log module.

#### Scenario: 纯 JdbcTemplate 实现

- **WHEN** 检查 `ChatMessageRepository` 与 `ChatTurnUsageRepository` 的依赖
- **THEN** 仅依赖 `spring-jdbc`，无 JPA / MyBatis 依赖
