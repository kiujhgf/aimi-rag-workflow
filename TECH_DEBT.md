# 未完成任务与技术债务

> 本文件用于集中记录项目中的未完成任务与技术债务，纳入版本控制、随代码一起演进。
>
> **编辑约定**：此文件不会被我（AI 助手）随意改动，仅在用户明确要求时才编辑。
>
> **概念区分**：
>
> - **未完成任务**：并非技术问题，而是可延后、当前阶段无需立即完成的项。
> - **技术债务**：技术能力不足，无法判定问题是否真实存在，或不知如何修复/解决的项。

***

## 未完成任务（Deferred Work）

### W-001 循环多个图（循环子图）

- **模块/位置**：aimi-rag-core（FlowBuilder / Flow）
- **优先级**：中
- **状态**：待处理
- **说明**：方向明确（支持任意子图作为循环单元），属功能增强，可延后实现

### W-002 上下文压缩 Step（ContextCompressionStep）

- **模块/位置**：aimi-rag-generation
- **优先级**：中
- **状态**：待处理
- **说明**：方案二下 `ChatGenerationStep` 仅透传 `List<Message>`，压缩需作为独立 Step 实现；方向明确（LLM 摘要压缩、重要性/时间衰减、多级记忆），可延后实现

### W-004 会话记忆管理 Step（记忆加载 / 写回）

- **模块/位置**：aimi-rag-generation
- **优先级**：中
- **状态**：待处理
- **说明**：方案二下多轮记忆由独立 Step 负责——按 `conversation_id` 读取历史并拼装完整 `List<Message>` 放入 ctx，本轮结束后写回存储，与 `ChatGenerationStep` 解耦；方向明确，可延后实现

### W-005 ChatGenerationStep 暴露 token 消耗（Usage）

- **模块/位置**：aimi-rag-generation（ChatGenerationStep）
- **优先级**：中
- **状态**：待处理
- **说明**：当前 `doProcess` 仅返回文本，丢弃了 `ChatResponse.getMetadata().getUsage()`；需提供方式将 promptTokens / completionTokens / totalTokens 写入 FlowContext 或富结果 DTO；方向明确，可延后实现

### W-008 聊天记忆/历史的 starter 自动装配

- **模块/位置**：aimi-rag-log-spring-boot-starter（扩展 `ChatMessageRepository` / `ChatTurnUsageRepository` / 默认 `ChatMemory` / `ChatHistoryRecordingAdvisor` 自动装配）
- **优先级**：低
- **状态**：待处理
- **说明**：本次 `chat-memory-history-management` 变更中，starter 自动装配部分暂缓——即 DataSource 存在时自动装配 `ChatMessageRepository` / `ChatTurnUsageRepository`、默认 `ChatMemory` bean、`ChatHistoryRecordingAdvisor`（含 `aimi.rag.chat.history.enabled` 开关），并在 `LogSchemaInitializer` 中建 `chat_message` / `chat_turn_usage` 表。用户明确「暂时不做，需要时再提需求」；方向明确（复用现有 `LogPersistenceAutoConfiguration` 的 DataSource 解析与建表机制），可延后实现。

### 已完成（Completed）

### W-003 aimi-rag-log 可插拔化（去 JPA/MyBatis 依赖）

- **模块/位置**：aimi-rag-log
- **优先级**：中
- **状态**：已完成（`refactor-log-spring-jdbc`）
- **说明**：已统一为纯 `spring-jdbc` 模块，通过显式方言支持多种数据库；旧 JPA/MyBatis 模块已移除，仓储、监听器与建表均由使用方手动构造或调用

### W-006 ChatGenerationStep 支持 LLM 结构化输出

- **模块/位置**：aimi-rag-generation（ChatGenerationStep）
- **优先级**：中
- **状态**：已完成（`StructuredChatGenerationStep`，基于 `ChatClient.entity()`）
- **说明**：已新增 `StructuredChatGenerationStep<T>`，通过 `ChatClient...call().entity(StructuredOutputConverter)` 返回结构化对象；`ChatGenerationStep` 保持文本输出

### W-007 aimi-rag-log Spring Boot Starter 自动装配

- **模块/位置**：aimi-rag-log-spring-boot-starter / aimi-rag-log
- **优先级**：中
- **状态**：已完成（`add-log-spring-boot-starter`）
- **说明**：当前 `aimi-rag-log` 按约定不提供任何 AutoConfiguration / 自动注入 / 配置项，使用方需手动创建 `LogSchemaInitializer`、`FlowExecutionJpaRepository`、`PersistentExecutionListener` 并自行调用 `initialize()` 与 `flow.addListener(...)`。可另建独立 starter 模块，将这些装配收敛到自动配置中，方便 Spring Boot 项目开箱使用。需明确：① 配置项范围（方言覆盖、`flow_name`、是否启动时建表、输入快照的 includeKeys 与大小上限、是否自动注册监听器）；② 与现有 `DataSource` 的协作方式（`@ConditionalOnSingleCandidate`、多数据源时如何选择）；③ 全部 Bean 使用 `@ConditionalOnMissingBean` 允许用户覆盖；④ starter 自身的 Spring Boot 版本与依赖边界，避免把 Boot 依赖反向引入 `aimi-rag-log`；⑤ 自动建表默认关闭还是开启（`initialize()` 失败仅 WARN，开启后可能掩盖结构问题）。
- **实现**：新增 `aimi-rag-log-spring-boot-starter`。`LogPersistenceAutoConfiguration` 自动提供 `FlowExecutionRepository`、`PersistentExecutionListener` 与 `LogSchemaInitializer`，并通过 `BeanPostProcessor` 把容器内所有 `FlowExecutionListener` 挂到所有 Spring 管理的 `Flow` bean；全部 Bean 可覆盖，`aimi.rag.log.enabled=false` 可关闭。数据源支持单库/`@Primary`，或用 `aimi.rag.log.data-source-bean-name` 显式指定。`aimi.rag.log.initialize-schema` 默认 `true`，启动时调用 `LogSchemaInitializer.initialize()`；`flow_name` 与输入快照配置暂未提供。

## 技术债务（Technical Debt）

### TD-001 Flow 嵌套支持（子流程）

- **位置**：aimi-rag-core（Flow / Step）
- **类型**：待明确方案
- **优先级**：中
- **状态**：待处理
- **说明**：初步方案为新增包装 Step（`FlowStep`/`SubFlowStep`）传入 `Flow`，但不确定能否覆盖嵌套 + 循环 / 并行等所有场景，且输入/输出映射、`FlowContext` 共享或隔离、监听器/持久化/错误路由如何衔接尚未明确

### TD-002 JdbcChatMemory 是否可避免 spring-jdbc 依赖

- **位置**：aimi-rag-generation
- **类型**：待学习调研
- **优先级**：低
- **状态**：待处理
- **说明**：不确定能否用纯 JDBC（`DataSource`/`Connection`）或复用现有 log 设施替代 `JdbcTemplate`，需调研后再判定

### TD-003 ChatMemory 的「历史」与「记忆」区分

- **位置**：aimi-rag-generation
- **类型**：待明确方案
- **优先级**：低
- **状态**：待处理
- **说明**：Spring AI `ChatMemory` 只存原始对话历史而非语义化记忆；不确定「历史」与「记忆」该分开存还是合并存，存储模型尚不明确

### TD-004 多模态支持

- **位置**：aimi-rag-mineru（唯一的文件→文本入口）；aimi-rag-ingest、aimi-rag-embedding、aimi-rag-es、aimi-rag-query、aimi-rag-generation（整条链路仅处理文本）
- **类型**：待明确方案（代码与文档未定义多模态的范围、目标形态与实现方式）
- **优先级**：待补充（代码与文档中没有优先级依据）
- **状态**：待处理（代码中尚无多模态实现，也无相关变更记录）
- **说明**：现状——① 非文本入口仅有 `MineruParseStep`，输入 `List<SourceFile>`（文件名+字节），经 MinerU `/file_parse` 解析后只从 `results.<文件名>.md_content` 提取纯文本 `List<String>`，请求固定 `return_md=true`；`mineru-document-parse-step` 提案已明确「不包含」解析后图片/表格本地落盘、多格式产物（docx/html/latex/zip）下载与异步任务。② ingest 链路为纯文本：`InputStep`/`ChunkingStep` 基于 `List<String>`，`Chunk` 仅含文本 `content` + `metadata`，`TextToEmbed.text` 为 `String`，`IngestOpenAiEmbeddingStep` 只对文本调用 OpenAI 嵌入；`precomputed-vector-support` 中的「图像向量」实际是 `name="image"` 的文本向量，而非图像 embedding。③ 存储/检索为纯文本：`StorageItem.getContent()` 返回 `String`，`DefaultStorageItemConverter` 写入 `content` + `_vector`，`SearchResult` 仅有文本 `content` 与 `score`。④ 生成侧 `ChatGenerationStep` 虽原样透传 `List<Message>`，但项目内只构造 `SystemMessage`/`UserMessage` 纯文本消息，未引用 Spring AI 的 `Media`/多模态能力。缺口——图片、PDF 扫描页、音视频均无原生输入与向量化；解析出的 markdown 图片引用未下载/落盘；无图搜文、文搜图等跨模态检索；无多模态 LLM 生成与相关测试；ES 侧无图像字段与多向量 mapping 约定（`<name>_vector` 需调用方自建 mapping）。

### TD-005 Tool calling 实现

- **位置**：aimi-rag-generation（`ChatGenerationStep` / `CompressingChatMemory` / `ChatHistoryRecordingAdvisor`）；aimi-rag-log（`chat_message` / `chat_turn_usage`）
- **类型**：待明确方案
- **优先级**：中
- **状态**：待处理
- **说明**：当前聊天记忆/历史设计明确将 tool calling 列为 Non-Goal，但存在已知限制——① Spring AI 官方 `JdbcChatMemoryRepository` 不支持 tool call 消息：含 tool calls 的 `AssistantMessage` 与 `ToolResponseMessage` 在保存时被静默过滤，检索历史时不会出现；② 自研 `chat_message.message_role` 目前仅规划 `user`/`assistant`/`system`，`CompressingChatMemory` 的摘要/窗口策略也未定义如何容纳 tool 调用序列（function call → tool response → 最终回答）。未来启用 tool calling 时需明确：是改用 Spring AI Session Memory（官方推荐方向，且 `ChatMemory` API 自 2.1.0 起废弃、3.0.0 移除）还是扩展自研 repository 支持 tool 消息类型；以及 `chat_message` 是否需要新增 `tool`/`function` 角色、`chat_turn_usage` 是否需要按 tool 子调用拆分 token 计量。

### TD-006 MCP 支持

- **位置**：aimi-rag-generation（`ChatGenerationStep` 的 advisor 链 / tool callback）；aimi-rag-core（是否新增独立 Step 或组件）
- **类型**：待明确方案
- **优先级**：中
- **状态**：待处理
- **说明**：项目当前无 MCP（Model Context Protocol）接入能力，`ChatGenerationStep` 只做纯文本生成，无法通过标准协议接入外部工具/数据源。Spring AI 提供 `spring-ai-starter-mcp-client` 等 MCP 客户端支持，但接入方案未定——① 是否引入该 starter 及其版本、依赖边界；② MCP tool 如何注册进 `ChatClient`（tool callback）并与现有 advisor 链（`MessageChatMemoryAdvisor` / `ChatHistoryRecordingAdvisor`）协作；③ 是否抽象为独立 `Step`/组件，MCP 的 tools/resources/prompts 如何映射到 `FlowContext` 的输入输出；④ 与 TD-005 的先后关系：MCP 工具调用本质依赖 tool calling，而当前记忆/历史层尚不支持 tool 消息，二者需统筹设计（MCP 落地前可能需先解决 TD-005）。
