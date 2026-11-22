## ADDED Requirements

### Requirement: 模块与依赖收敛（纯 Java 库）

系统 SHALL 提供单一 `aimi-rag-log` 模块承担执行日志持久化，其持久化仅依赖 `spring-jdbc`（`JdbcTemplate`），MUST NOT 引入 JPA / Hibernate / MyBatis 依赖，MUST NOT 依赖任何 `spring-boot*` 构件。模块 MUST NOT 提供 AutoConfiguration、`META-INF/spring/...AutoConfiguration.imports`、`spring.factories` 或任何自动注入机制，所有对象由使用方手动构造。`aimi-rag-log-jpa` 与 `aimi-rag-log-mybatis` 模块及其 JPA 实体、MyBatis Mapper MUST 被移除；原 Spring Data `*JpaRepository` 接口形态 MUST 被移除。

#### Scenario: 模块结构

- **WHEN** 查看根 `pom.xml` 的 `<modules>` 与新模块依赖树
- **THEN** 模块列表包含 `aimi-rag-log` 且不包含 `aimi-rag-log-jpa` / `aimi-rag-log-mybatis`；`aimi-rag-log` 的依赖中不存在 `jakarta.persistence`、Hibernate、MyBatis、`spring-boot*` 相关构件

#### Scenario: 无自动装配

- **WHEN** 检查 `aimi-rag-log` 产物 jar 的内容
- **THEN** 不存在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 与 `META-INF/spring.factories`，不存在任何 AutoConfiguration 类

#### Scenario: 旧包名引用不可用

- **WHEN** 编译期引用 `io.github.aimi.rag.workflow.log.jpa.**` / `log.mybatis.**` 下任何类型
- **THEN** 编译失败（旧包已随旧模块删除）

### Requirement: 三个仓储类保留类名并改为 spring-jdbc 实现

`aimi-rag-log` SHALL 在 `io.github.aimi.rag.workflow.log.repository` 包下提供 `FlowExecutionJpaRepository`、`StepExecutionJpaRepository`、`StepFailureJpaRepository` 三个**普通类**（类名与旧实现一致，仅为兼容保留）。三者 MUST 通过构造器注入 `JdbcTemplate` 与 `JdbcLogRepositoryDialect`，MUST NOT 使用 `jakarta.persistence` 注解或继承 Spring Data 接口。其中 `FlowExecutionJpaRepository` MUST 实现 core 的 `FlowExecutionRepository` 接口并组合另外两个类，且 MUST 提供 `(DataSource)`、`(DataSource, JdbcLogRepositoryDialect)`、`(DataSource, InputContextSerializer)`、`(JdbcTemplate, JdbcLogRepositoryDialect, InputContextSerializer)` 构造器；`(DataSource)` 构造器 MUST 自动识别方言。

#### Scenario: 类形态

- **WHEN** 反射检查 `FlowExecutionJpaRepository` / `StepExecutionJpaRepository` / `StepFailureJpaRepository`
- **THEN** 三者均为普通 class（非 interface），位于 `io.github.aimi.rag.workflow.log.repository` 包，类上与方法上不存在 JPA / Spring Data 注解；`FlowExecutionJpaRepository` 实现 `FlowExecutionRepository`

#### Scenario: 手动构造即可用

- **WHEN** 在非 Spring 项目中 `new FlowExecutionJpaRepository(dataSource)`（表已存在）
- **THEN** 无需任何 Spring 上下文即可执行接口全部方法

### Requirement: 数据库方言抽象参考 JdbcChatMemoryRepositoryDialect

`aimi-rag-log` SHALL 提供 `JdbcLogRepositoryDialect` 接口，命名与使用方式尽可能参考 Spring AI `JdbcChatMemoryRepositoryDialect`。接口 MUST 直接提供本模块 DML 与 DDL 的完整 SQL：创建/更新 flow_execution、插入/查询 step_execution、插入 step_failure，以及三表和两索引的建表/建索引语句。DML MUST 使用 `?` 参数占位。模块 MUST 内置 `MysqlLogRepositoryDialect`（MySQL/MariaDB 共用）、`PostgresLogRepositoryDialect`、`H2LogRepositoryDialect`、`OracleLogRepositoryDialect`、`SqlServerLogRepositoryDialect`、`SqliteLogRepositoryDialect` 及 ANSI `GenericLogRepositoryDialect`。

#### Scenario: 方言差异由实现类承担

- **WHEN** 分别获取 MySQL、PostgreSQL、Oracle、SQL Server、SQLite 方言的建表与 DML SQL
- **THEN** SQL 符合各数据库语法（如 PostgreSQL 时间戳为 `TIMESTAMP(6) WITH TIME ZONE`，SQL Server 为 `DATETIMEOFFSET(6)`，SQLite 为 `TEXT`，Oracle 长文本为 `CLOB`），且仓储与初始化器中不存在按数据库名的 if/switch 分支

#### Scenario: 接口直接提供 SQL

- **WHEN** 仓储需要插入执行记录或初始化器需要建表
- **THEN** 直接调用方言对应的 `get...Sql()` 方法，不由核心代码拼接列类型、IF NOT EXISTS 或 upsert 语法

### Requirement: 方言识别参考 JdbcChatMemoryRepositoryDialect

`JdbcLogRepositoryDialect` SHALL 提供静态 `from(DataSource)`：通过 `DatabaseMetaData.getURL()` 获取 JDBC URL，转小写后按 `postgresql`、`mysql`/`mariadb`、`sqlserver`、`h2`、`oracle`、`sqlite` 识别产品，连接 MUST 被正确关闭。模块 SHALL 提供静态 `resolve(dataSource, explicitDialect)`：显式方言 MUST 优先；当显式方言与自动识别实现类不一致时 MUST 记录 WARN。模块 MUST NOT 通过外部配置项指定方言，也 MUST NOT 额外暴露公共 `DatabaseDialectResolver`。

#### Scenario: 自动识别

- **WHEN** 分别以 H2 / MySQL / PostgreSQL 数据源调用 `JdbcLogRepositoryDialect.from(dataSource)`
- **THEN** 分别返回 `H2LogRepositoryDialect` / `MysqlLogRepositoryDialect` / `PostgresLogRepositoryDialect`

#### Scenario: MariaDB 复用 MySQL 方言

- **WHEN** JDBC URL 包含 `mariadb`
- **THEN** 返回 `MysqlLogRepositoryDialect`

#### Scenario: 显式传入优先并警告不匹配

- **WHEN** 使用方以 H2 数据源构造 `new FlowExecutionJpaRepository(dataSource, new PostgresLogRepositoryDialect())`
- **THEN** 仓储使用 `PostgresLogRepositoryDialect`，并记录显式方言与检测方言不一致的 WARN

#### Scenario: 未识别数据库兜底

- **WHEN** JDBC URL 不包含任何内置产品标识
- **THEN** 返回 `GenericLogRepositoryDialect` 并记录 WARN，不默认使用 PostgreSQL 方言

#### Scenario: 元数据读取失败

- **WHEN** 获取连接或读取 JDBC URL 失败
- **THEN** 抛出带原始 cause 的 `IllegalStateException`，不静默退回 PostgreSQL 方言

### Requirement: 建表与索引由使用方显式触发

模块 SHALL 提供 `LogSchemaInitializer` 工具类，通过方言生成 `flow_execution`、`step_execution`、`step_failure` 三表及 `execution_id` 上两个索引的 DDL，表名、列名、列长度 MUST 与旧实现保持一致。建表 MUST 仅在使用方显式调用 `initialize()` 时执行，模块 MUST NOT 在类加载、构造或 Spring 生命周期中自动执行 DDL，也 MUST NOT 提供建表开关配置项。单条 DDL 失败时 MUST 记录 WARN 并继续执行后续语句，不抛出异常。

#### Scenario: 显式调用建表

- **WHEN** 使用方调用 `new LogSchemaInitializer(dataSource).initialize()`
- **THEN** 三表与两索引按当前方言创建

#### Scenario: 不调用则不建表

- **WHEN** 使用方仅构造仓储或 listener，从未调用 `LogSchemaInitializer.initialize()`
- **THEN** 不执行任何 DDL

#### Scenario: 重复执行幂等

- **WHEN** 同一数据库上连续调用两次 `initialize()`
- **THEN** 第二次不导致失败（IF NOT EXISTS 或等效容错）

#### Scenario: 建表失败容错

- **WHEN** 当前数据库账号无 DDL 权限导致建表语句失败
- **THEN** 记录 WARN 日志并继续后续语句，`initialize()` 正常返回

### Requirement: input_context 默认不保存，可选精简快照

`FlowExecutionJpaRepository` MUST 默认将 `input_context` 写为 `null`，MUST NOT 自动序列化完整 `FlowContext`。模块 SHALL 提供 `InputContextSerializer` 接口，使使用方可以通过构造器显式启用输入快照；并提供 `JacksonInputContextSerializer`，只序列化使用方指定的 key，默认最大 16 KB 且允许调整。序列化或快照生成失败 MUST NOT 阻断执行日志主流程。

#### Scenario: 默认不保存输入快照

- **WHEN** 使用 `new FlowExecutionJpaRepository(dataSource)` 创建执行记录
- **THEN** `input_context` 写入 `null`，不调用 Jackson 序列化完整 Context

#### Scenario: 只保存指定 key

- **WHEN** 使用方配置 `JacksonInputContextSerializer`，includeKeys 为 `question`、`userId`，而 Context 还包含 `messages`、`answer`
- **THEN** `input_context` 的 JSON 只包含存在的 `question` / `userId`，不包含 `messages` / `answer`

#### Scenario: 超长快照

- **WHEN** 指定 key 生成的 JSON 超过 `maxChars`
- **THEN** `input_context` 写入 `{"truncated":true}`，记录 WARN，不写入半截 JSON

#### Scenario: 单个值不可序列化

- **WHEN** 某个被包含 key 的 value 无法被 Jackson 序列化
- **THEN** 跳过该 key 并记录 WARN，其他可序列化 key 仍被保存

#### Scenario: 自定义序列化器失败

- **WHEN** 使用方传入的 `InputContextSerializer` 抛出运行异常
- **THEN** 仓储记录 WARN，将 `input_context` 写为 `null`，`createExecution` 正常完成

### Requirement: 通过 spring-jdbc 实现 FlowExecutionRepository

模块 SHALL 通过 `FlowExecutionJpaRepository` 提供 `FlowExecutionRepository` 接口的实现，基于 `JdbcTemplate` 与 `JdbcLogRepositoryDialect` 完成接口全部五个方法，语义与旧 JPA 实现一致；`input_context` 按“可选精简快照”需求处理。各方法 MUST 以单条 SQL 完成，不强制要求 Spring 事务上下文。

#### Scenario: 创建执行记录

- **WHEN** 调用 `createExecution(flowName, context)`，context 含 execution id
- **THEN** `flow_execution` 插入一行：id 取自 context、status 为 `RUNNING`、start_time 为当前时间、input_context 按所配置的 `InputContextSerializer` 写入（默认 `null`）

#### Scenario: 保存步骤成功记录

- **WHEN** 调用 `saveStepExecution(context, record)`
- **THEN** `step_execution` 插入一行，字段与 record 的 stepName/status/startTime/endTime 一致，execution_id 取自 context

#### Scenario: 保存步骤失败记录

- **WHEN** 调用 `saveStepFailure(context, record)`
- **THEN** `step_failure` 插入一行，包含 errorMessage/errorStack/timestamp，execution_id 取自 context

#### Scenario: 更新执行状态

- **WHEN** 调用 `updateExecutionStatus(context, "COMPLETED")`
- **THEN** 对应 execution id 的 `flow_execution` 行 status 更新为 `COMPLETED` 且 end_time 被设置

#### Scenario: 查询执行历史

- **WHEN** 对同一 execution id 依次保存两条步骤记录后调用 `getExecutionHistory(executionId)`
- **THEN** 返回按 start_time 升序的两条 `StepExecutionRecord`，字段值与写入一致

#### Scenario: Context 含不可序列化对象但默认无快照

- **WHEN** 默认仓储的 context 中含有 `Flux` 等无法被 Jackson 序列化的对象时调用 `createExecution`
- **THEN** 不进行 Context 序列化，input_context 写入 null，方法正常返回不抛异常
