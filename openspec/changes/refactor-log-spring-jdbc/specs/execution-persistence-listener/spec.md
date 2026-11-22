## ADDED Requirements

### Requirement: PersistentExecutionListener 归属 aimi-rag-log

`PersistentExecutionListener` SHALL 位于 `aimi-rag-log` 模块的 `io.github.aimi.rag.workflow.log` 包下，`aimi-rag-core` MUST NOT 再包含该类。迁移后其回调语义 MUST 与迁移前完全一致：onFlowStart 创建执行记录、onStepStart 记录步骤开始时间、onStepSuccess 保存步骤记录、onStepFailure 保存失败记录（含堆栈）、onFlowComplete / onFlowFailure 更新执行状态为 COMPLETED / FAILED。

#### Scenario: 包位置

- **WHEN** 编译期引用 `io.github.aimi.rag.workflow.core.flow.PersistentExecutionListener`
- **THEN** 编译失败；改用 `io.github.aimi.rag.workflow.log.PersistentExecutionListener` 可正常编译

#### Scenario: 生命周期语义不变

- **WHEN** 使用方手动构造 listener 并 `flow.addListener(...)` 后，一个包含单个 Step 的 Flow 执行成功
- **THEN** 数据库中产生 1 条 RUNNING→COMPLETED 的 flow_execution 记录与 1 条 step_execution 记录，字段与迁移前一致

### Requirement: 手动构造与挂载，无自动装配

`aimi-rag-log` 模块 MUST NOT 提供任何 AutoConfiguration / 自动注入 / 自动挂载机制，`PersistentExecutionListener` MUST NOT 被自动注册为 bean 或自动添加到 `Flow`。使用方 SHALL 通过构造器显式创建并调用 `Flow.addListener(...)` 挂载，该使用方式在 Spring Boot 与非 Spring 项目中 MUST 一致。listener SHALL 提供 `PersistentExecutionListener(FlowExecutionRepository)` 与 `PersistentExecutionListener(DataSource)` 两个构造器；后者 MUST 内部基于 `JdbcTemplate` 与 `JdbcLogRepositoryDialect.from(dataSource)` 自动识别方言并创建 `FlowExecutionJpaRepository`，且 MUST NOT 自动执行建表。

#### Scenario: 手动构造并挂载

- **WHEN** 使用方 `new PersistentExecutionListener(repository)` 并 `flow.addListener(...)` 后执行 Flow
- **THEN** 执行日志按生命周期回调写入数据库

#### Scenario: DataSource 构造器

- **WHEN** 使用方 `new PersistentExecutionListener(dataSource)` 并挂载到 Flow（表已由 `LogSchemaInitializer` 或手工 DDL 创建）
- **THEN** listener 内部自动识别方言并创建仓储，日志正常写入；构造过程不执行任何 DDL

#### Scenario: 自定义持久化实现

- **WHEN** 使用方自行实现 core 的 `FlowExecutionRepository` 并传入 `new PersistentExecutionListener(customRepository)`
- **THEN** listener 使用自定义实现写日志，无需任何条件装配机制

#### Scenario: 无 Spring 环境可用

- **WHEN** 在没有任何 Spring 上下文的应用中手动构造仓储与 listener 并执行 Flow
- **THEN** 日志持久化正常工作
