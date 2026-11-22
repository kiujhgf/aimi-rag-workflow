## Why

执行日志持久化目前由 `aimi-rag-log-jpa`（Spring Data JPA + Hibernate）和 `aimi-rag-log-mybatis`（MyBatis）两套实现并行维护，存在三个问题：

1. **重复建设**：同一组表（`flow_execution` / `step_execution` / `step_failure`）两套实体、两套配置，行为容易漂移。
2. **依赖过重**：仅 3 张表、5 个写读操作的场景引入完整 ORM，启动慢、传递依赖多，且 JPA 的 `ddl-auto` 与手工建表并存容易冲突。
3. **方言逻辑分散**：`DatabaseInitializer` 里已有手写的按数据库类型拼 DDL 的 if/switch 分支，与 Hibernate 方言是两套并行体系，无法复用也无法扩展。

统一收敛为一个基于 `spring-jdbc` 的 `aimi-rag-log` 模块，用显式的数据库方言抽象替代 ORM 方言与散落的 if/switch，可以把持久化层做薄、做透、可测试。

## What Changes

- **BREAKING** 删除 `aimi-rag-log-jpa` 与 `aimi-rag-log-mybatis` 两个子模块，以及其中的 JPA 实体（`FlowExecution` / `StepExecution` / `StepFailure`）、MyBatis Mapper 与各自 AutoConfiguration。
- **BREAKING** 根 `pom.xml` 移除上述两个 module 与相关依赖管理；新增 `aimi-rag-log` 子模块。
- 新增 `aimi-rag-log` 模块：**纯 Java 持久化库**，仅依赖 `spring-jdbc`（不依赖 Spring Boot、不引入 JPA / Hibernate / MyBatis），通过 `JdbcTemplate` 实现 core 的 `FlowExecutionRepository` 业务接口。**不提供任何 AutoConfiguration、不做自动注入**（需求方确认：workflow 并非只在 Spring Boot 项目使用，其他项目也应可用），所有对象由使用方手动构造。
- 新增数据库方言抽象 `JdbcLogRepositoryDialect`，尽可能参考 Spring AI `JdbcChatMemoryRepositoryDialect`：接口直接提供各仓储操作及建表/索引的 SQL 语句，方言实现封装完整 SQL 差异，而非由核心代码拼接类型和语句；内置 MySQL/MariaDB、PostgreSQL、H2、Oracle、SQL Server、SQLite 六种方言及通用兜底方言。
- `JdbcLogRepositoryDialect.from(DataSource)` 参考 Spring AI 通过 `DatabaseMetaData.getURL()` 识别 JDBC URL 中的产品名；显式传入方言优先，并在与识别结果不一致时记录 WARN。识别失败与未知产品的处理不照搬 Spring AI 默认 PostgreSQL 的行为：元数据读取失败抛出明确异常，未知产品使用通用方言并记录 WARN。
- 提供 `LogSchemaInitializer` 工具类：由方言生成三表 + 两索引 DDL，**由使用方显式调用**执行（无 `@PostConstruct`、无配置开关）。
- `input_context` 改为**可选精简快照**：默认写 `null`，不再自动序列化完整 `FlowContext`；使用方可传入 `InputContextSerializer`，通过 Jackson 实现只保存指定 key，并限制快照大小，避免日志库膨胀与无关/敏感数据落库（需求方确认）。
- 将 `PersistentExecutionListener` 从 `aimi-rag-core` 的 `io.github.aimi.rag.workflow.core.flow` 包迁移到 `aimi-rag-log` 的 `io.github.aimi.rag.workflow.log` 包。**BREAKING**：原 core 包下的该类删除，引用方需更新 import。由使用方自行 `new PersistentExecutionListener(...)` 并 `flow.addListener(...)`，不自动挂载。
- 表结构保持不变（`flow_execution` / `step_execution` / `step_failure` 三表及两个索引），沿用现有列名与长度，保证已有数据可平滑迁移。

### 关于三个 `*JpaRepository` 类的处理（已与需求方确认）

**保留 `FlowExecutionJpaRepository` / `StepExecutionJpaRepository` / `StepFailureJpaRepository` 三个类名不变**，但形态从 Spring Data JPA 接口（`extends JpaRepository`）改为普通 class，内部通过 `JdbcTemplate` + `JdbcLogRepositoryDialect` 实现，包迁至 `aimi-rag-log` 的 `io.github.aimi.rag.workflow.log.repository`：

| 类名（不变） | 新形态与职责 |
|------|------|
| `FlowExecutionJpaRepository` | 普通 class，`implements FlowExecutionRepository`（core 业务接口）。负责 `flow_execution` 表读写，并组合下面两个类完成接口全部五个方法；类名为兼容保留，实现与 JPA 无关 |
| `StepExecutionJpaRepository` | 普通 class，负责 `step_execution` 表的 INSERT 与按 executionId 查询（按 start_time 升序） |
| `StepFailureJpaRepository` | 普通 class，负责 `step_failure` 表的 INSERT |

三者均通过构造器注入 `JdbcTemplate` 与 `JdbcLogRepositoryDialect`，不含任何 `jakarta.persistence` / Spring Data 注解。

## Capabilities

### New Capabilities

- `jdbc-log-persistence`: `aimi-rag-log` 基于 `spring-jdbc` 的执行日志持久化能力——方言抽象与解析、三表 DDL/索引初始化、通过 `JdbcTemplate` 实现 `FlowExecutionRepository` 的全部读写操作。
- `execution-persistence-listener`: `PersistentExecutionListener` 的归属与使用方式——迁移至 `aimi-rag-log`，**不提供自动装配/自动挂载**，由使用方显式构造并挂载到 `Flow`；回调语义保持不变（flow/step 生命周期内写库）。

### Modified Capabilities

（无。core 的 `FlowExecutionListener` / `FlowExecutionRepository` 接口签名不变；本次变化是实现归属与持久化技术栈，不是接口需求变更。）

## Impact

- **模块**：删除 `aimi-rag-log-jpa`、`aimi-rag-log-mybatis`；新增 `aimi-rag-log`；`aimi-rag-core` 仅删除 `PersistentExecutionListener` 一个类。
- **依赖**：新增 `spring-jdbc`（纯 JDBC，不含 `spring-boot-starter*`）；移除 `spring-boot-starter-data-jpa`、Hibernate、MyBatis 相关依赖；保留 `jackson-databind`（可选输入快照实现）与 `slf4j`。
- **API 破坏性**：
  - `io.github.aimi.rag.workflow.core.flow.PersistentExecutionListener` 被移除，改用 `io.github.aimi.rag.workflow.log.PersistentExecutionListener`。
  - 任何直接注入 `*JpaRepository` 或 MyBatis Mapper 的外部代码将失效（本仓库内仅 `aimi-rag-log-jpa` 内部使用，无其他引用）。
  - 旧 AutoConfiguration 与 `aimi.rag.step-log-jdbc` / `aimi.rag.auto-init-tables` 等配置项全部移除，不再提供 Spring Boot 开箱即用装配，需手动构造。
- **数据**：表名、列名、索引保持不变；既有库表无需重建。
- **测试**：原 JPA/MyBatis 模块测试随模块删除；新模块用 H2 做单元测试，并用方言矩阵做 DDL 字符串断言。
