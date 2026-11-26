## 1. 依赖与基础

- [x] 1.1 `aimi-rag-generation/pom.xml` 新增 `spring-ai-starter-model-chat-memory-repository-jdbc`（可选 JDBC 记忆）
- [x] 1.2 全模块编译基线通过（`mvn -q -DskipTests compile`）

## 2. 历史持久化（aimi-rag-log）

- [x] 2.1 `JdbcLogRepositoryDialect` 新增 `chat_message` / `chat_turn_usage` 相关 SQL 方法（建表、唯一/普通索引、insert/upsert、select-by-conversation、update-usage-status）
- [x] 2.2 `AbstractLogRepositoryDialect` 提供两张表默认 DDL/CRUD 实现，复用 `varcharType` / `timestampType` / `textType`
- [x] 2.3 各方言（Postgres/MySQL/SqlServer/Oracle/H2/SQLite/Generic）按需覆盖类型差异
- [x] 2.4 `LogSchemaInitializer.initialize()` 追加两张表建表与索引语句（`chat_message` 唯一索引 + 会话索引；`chat_turn_usage` 唯一索引 + 执行索引）
- [x] 2.5 新增 `ChatMessageRecord`（POJO：conversationId、turnSequence、messageIndex、messageRole、content、createdTime）与 `ChatTurnUsageRecord`（POJO：conversationId、turnSequence、model、executionId、status、errorMessage、promptTokens、completionTokens、totalTokens、startTime、endTime）
- [x] 2.6 新增 `ChatMessageRepository`（纯 `JdbcTemplate`）：`saveMessages`（user 行 index=0、assistant 行 index=1，无 token，upsert 幂等）、`findByConversationId`
- [x] 2.7 新增 `ChatTurnUsageRepository`（纯 `JdbcTemplate`）：`saveUsage`（每轮一行，token 归属此表）、`markError(conversationId, turnSequence, msg)`、`markCancelled(conversationId, turnSequence)`；`turn_sequence = MAX+1` 并在唯一键冲突时重试
- [x] 2.8 单测：H2/HSQLDB 内存库验证两张表建表与索引、保存消息两行 + 用量一行、重复保存幂等、按会话升序查询、markError/markCancelled

## 3. ChatGenerationStep 接入 advisor 与 conversationId

- [x] 3.1 新增 `ConversationIdResolver` 接口（`String resolve(FlowContext)`）及默认实现（读 `conversation_id`，空返回 null）
- [x] 3.2 `ChatGenerationStep` 持有 `ChatMemory`、`ConversationIdResolver`、构建期兜底 UUID、`historyEnabled` 标志
- [x] 3.3 `doProcess` 解析 conversationId（空则兜底 UUID），call/stream 均通过 `advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))` 传参
- [x] 3.4 构建 `ChatClient` 时挂载 `MessageChatMemoryAdvisor`（工作记忆）与（开启时）`ChatHistoryRecordingAdvisor`（历史），顺序为历史在外、记忆在内
- [x] 3.5 Builder / ConfigurableBuilder 新增 `chatMemory(...)`、`conversationIdResolver(...)`、`historyEnabled(boolean)`、`historyAdvisor(...)`；默认记忆 = `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`（maxMessages=20）
- [x] 3.6 提供 `jdbcChatMemory(JdbcTemplate)` 便捷入口，组装官方 `JdbcChatMemoryRepository` + `MessageWindowChatMemory`
- [x] 3.7 单测：同 conversationId 第二轮带历史、不同 id 隔离、无 id 用兜底 UUID、自定义 resolver 生效、自定义 ChatMemory 生效、`builder(ChatModel)`/ConfigurableBuilder 向后兼容

## 4. CompressingChatMemory（LLM 上下文压缩）

- [x] 4.1 新增 `CompressingChatMemory`（实现 `ChatMemory`），持有委托 `ChatMemoryRepository`、`ChatModel`/`ChatClient`、`TokenCountEstimator`、`maxTokens`、`keepRecentMessages`、摘要缓存
- [x] 4.2 `get(conversationId)`：取全量 → 估 token → 超阈值则把旧消息交 LLM 生成摘要，返回 `[摘要 SystemMessage] + 最近 N 条`；命中缓存直接返回
- [x] 4.3 `add(...)` 写 repository 并使摘要缓存失效；`clear(...)` 清 repository 与缓存
- [x] 4.4 压缩失败降级为不压缩的窗口消息，捕获异常不抛出
- [x] 4.5 单测：超阈值触发压缩（mock ChatModel 返回摘要）、未超阈值原样返回、缓存命中不重复调用、写入使缓存失效、压缩失败降级

## 5. ChatHistoryRecordingAdvisor（历史 + token + 流式补偿）

- [x] 5.1 新增 `ChatHistoryRecordingAdvisor`，实现 `CallAdvisor` + `StreamAdvisor`
- [x] 5.2 Call 路径：`before` 记录 user 行与 start_time；`after` 从 `ChatResponse.getMetadata().getUsage()` 取 token，落 user+assistant 两行，SUCCESS
- [x] 5.3 Stream 路径：聚合 chunk（复用 `ChatClientMessageAggregator` 或 reduce），`doOnNext` 原样透传不阻塞
- [x] 5.4 `doOnComplete` 落库 SUCCESS + 聚合 token；`doOnError` 落 ERROR；`doOnCancel` 落 CANCELLED
- [x] 5.5 conversationId / executionId / model 通过 advisor context map 显式传递，不依赖 ThreadLocal
- [x] 5.6 token 读不到时降级（写空，不抛异常）；确保 OpenAI 流式开启 `stream_options.include_usage`
- [x] 5.7 单测：call 落库 token 正确、stream 完成落库、stream 异常落 ERROR、stream 取消落 CANCELLED、chunk 透传未被阻塞

## 6. 语义收窄与文档

- [x] 6.1 `ChatMessageAssembler` 注释明确：仅负责 system/user prompt 组装，历史由 advisor 处理
- [x] 6.2 `ChatGenerationStep` 类注释更新：输入语义为「本轮消息」，历史来自 `ChatMemory`

## 7. 联调验证

- [x] 7.1 多轮 call 联调：第二轮请求含第一轮历史
- [x] 7.2 多轮 stream 联调：完成/异常/取消三种状态落库正确
- [x] 7.3 token 统计联调：`chat_turn_usage` 每轮一行，`total_tokens` 与实际用量一致
- [x] 7.4 压缩联调：超阈值后旧消息被摘要替代
- [x] 7.5 跨会话隔离与历史表全量保留验证
- [x] 7.6 全模块 `mvn -q test` 回归通过
