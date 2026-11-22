## Context

当前持久化层是两套并行实现：

- `aimi-rag-log-jpa`：`JpaFlowExecutionRepository`（实现 core 的 `FlowExecutionRepository`）+ 三个 `*JpaRepository`（Spring Data）+ 三个 `@Entity`，另有 `DatabaseInitializer` 用 `JdbcTemplate` 手写方言 DDL。AutoConfiguration 里还被迫加了一个 `BeanFactoryPostProcessor`，只为让 Hibernate `ddl-auto=validate` 等手工建表完成——这是 ORM 与手工 DDL 并存的直接代价。
- `aimi-rag-log-mybatis`：同三张表的 MyBatis Mapper 实现。

core 中的 `PersistentExecutionListener` 只依赖 `FlowExecutionRepository` 接口，与具体持久化技术无耦合，迁移成本仅限包名与装配方式。

约束：表结构（`flow_execution` / `step_execution` / `step_failure` 及 `execution_id` 两个索引）保持不变；core 的 `FlowExecutionRepository` / `FlowExecutionListener` 接口签名不变；Spring Boot 3.5 / Java 21。

## Goals / Non-Goals

**Goals:**

- 单一 `aimi-rag-log` 模块，**纯 Java 库**，仅依赖 `spring-jdbc`（不依赖 Spring Boot），完成执行日志的全部持久化，可在 Spring Boot / 非 Spring 等任意项目中使用。
- 方言成为显式扩展点：新增数据库只需新增一个 `JdbcLogRepositoryDialect` 实现，不改核心代码。
- 方言自动识别（`DatabaseMetaData`）+ 构造器显式传入覆盖。
- 所有对象（仓储、`PersistentExecutionListener`、建表初始化器）由使用方手动构造/调用；模块**不提供任何 AutoConfiguration、不自动注入、不自动建表**（需求方确认）。
- 去掉 JPA 后随之消除 `flowTablesBeforeEntityManagerFactory`、`flowListenerRegistrar` 等 Spring 协调代码。

**Non-Goals:**

- 不改变 `FlowExecutionRepository` 接口与 `PersistentExecutionListener` 的回调语义。
- 不引入连接池/事务管理的新抽象；各操作均为单条 SQL，不强制事务（需要时由使用方自行包裹）。
- 不做数据库 Migration 工具（Flyway/Liquibase）集成；建表由使用方显式调用 `LogSchemaInitializer`。
- 不提供 Spring Boot AutoConfiguration、`META-INF/spring/...imports` 与 `aimi.rag.*` 配置项。
- 不追求覆盖所有数据库的 SQL 全特性，只覆盖本模块实际用到的 DDL/DML 子集。
- 不实现 MyBatis 模块中超出 `FlowExecutionRepository` 接口范围的额外查询能力（如有），保持能力对等收缩。

## Decisions

### D1：用 `spring-jdbc` + `JdbcTemplate`，不用 JPA/MyBatis，也不用 `spring-data-jdbc`

- JPA/MyBatis：本次就是要移除的重依赖。
- `spring-data-jdbc`：仍带 Repository/实体映射体系与 `spring-data-commons` 传递依赖，对 3 张表而言收益低，且方言仍要自行处理。
- 裸 `JdbcTemplate`：API 稳定、无实体映射魔法、SQL 完全可控，方言差异可以直接落在 SQL 生成上，是最匹配的选择。
- 只引入 `spring-jdbc` 本身（含其传递的 `spring-core` / `spring-beans` / `spring-tx`），**不引入任何 `spring-boot*` 依赖**，保证非 Spring Boot 项目可用。

### D2：方言接口参考 JdbcChatMemoryRepositoryDialect，直接提供 SQL

新增 `io.github.aimi.rag.workflow.log.dialect.JdbcLogRepositoryDialect`，命名与使用方式尽可能对齐 Spring AI 的 `JdbcChatMemoryRepositoryDialect`：接口方法直接返回完整 SQL，而不是仅返回列类型再由核心代码拼接语句。

```java
public interface JdbcLogRepositoryDialect {

    static JdbcLogRepositoryDialect from(DataSource dataSource) { ... }

    static JdbcLogRepositoryDialect resolve(
            DataSource dataSource, JdbcLogRepositoryDialect explicitDialect) { ... }

    // DML
    String getInsertFlowExecutionSql();
    String getUpdateFlowExecutionStatusSql();
    String getInsertStepExecutionSql();
    String getSelectStepExecutionsSql();
    String getInsertStepFailureSql();

    // DDL
    String getCreateFlowExecutionTableSql();
    String getCreateStepExecutionTableSql();
    String getCreateStepFailureTableSql();
    String getCreateStepExecutionIndexSql();
    String getCreateStepFailureIndexSql();
}
```

- DML 统一使用 `?` 参数占位，与 Spring AI 方言的使用方式一致；MySQL `INSERT IGNORE`、PostgreSQL `ON CONFLICT DO NOTHING` 等差异直接落在各自方言的完整 SQL 中。
- DDL 也由方言直接提供完整语句，避免在核心代码出现数据库名分支；类型映射可作为各实现类的私有/共享辅助方法，不进入公共方言 API。
- 内置实现：`MysqlLogRepositoryDialect`（MySQL/MariaDB 共用）、`PostgresLogRepositoryDialect`、`H2LogRepositoryDialect`、`OracleLogRepositoryDialect`、`SqlServerLogRepositoryDialect`、`SqliteLogRepositoryDialect`，以及 `GenericLogRepositoryDialect`（ANSI 兜底）。
- 表名、列名、长度保持与旧实现一致；`LIMIT`/分页暂未使用，不增加无实际调用方的方法。
- 备选方案：直接用 Hibernate 方言做 SQL 生成——否决，会重新引入 Hibernate 依赖。

### D3：识别方式参考 Spring AI，显式方言优先

`JdbcLogRepositoryDialect.from(DataSource)` 参考 `JdbcChatMemoryRepositoryDialect.from(DataSource)`：

1. 通过 `dataSource.getConnection().getMetaData().getURL()` 获取 JDBC URL（try-with-resources 关闭连接）；
2. URL 转小写后按产品标识匹配：
   - `postgresql` → Postgres
   - `mysql` / `mariadb` → Mysql
   - `sqlserver` → SQL Server
   - `h2` → H2
   - `oracle` → Oracle
   - `sqlite` → SQLite
3. 未匹配时返回 `GenericLogRepositoryDialect` 并记录 WARN；**不照搬** Spring AI 当前版本“未知则默认 PostgreSQL”的行为，避免在不支持的数据库上静默执行错误 SQL。
4. 获取连接或元数据失败时抛出 `IllegalStateException("Could not detect dialect from datasource", cause)`；**不照搬** Spring AI 当前版本“异常也默认 PostgreSQL”的行为。

`resolve(dataSource, explicitDialect)` 参考 Spring AI Builder 的 `resolveDialect`：显式方言非空时优先返回显式值；同时尝试自动识别，若实现类不一致记录 WARN（识别失败仅 DEBUG，不影响显式方言使用）。显式方言为空时委托 `from(dataSource)`。不再单独提供公共 `DatabaseDialectResolver`。

### D4：保留三个 `*JpaRepository` 类名，改为普通 class 分层实现（需求方确认）

```
FlowExecutionRepository (core 接口)
        ▲
FlowExecutionJpaRepository               ← 普通 class，实现 core 接口；flow_execution 表读写 + 事务边界 + context JSON 序列化
        │  组合
StepExecutionJpaRepository               ← 普通 class，step_execution 表 INSERT / 按 executionId 查询
StepFailureJpaRepository                 ← 普通 class，step_failure 表 INSERT
        │  持有
   JdbcTemplate + JdbcLogRepositoryDialect ← SQL 方言差异全部由方言生成
```

- 三个类名保持与旧 Spring Data 接口完全一致（需求方明确要求），包迁至 `io.github.aimi.rag.workflow.log.repository`；类名中的 `Jpa` 仅为兼容保留，实现与 JPA 无关。
- 均为普通 class，可直接 new 出来单测，无接口继承体系、无 Spring 注解。构造器提供便捷与完整两档（以 `FlowExecutionJpaRepository` 为例）：
  - `FlowExecutionJpaRepository(DataSource dataSource)`：内部 `new JdbcTemplate(dataSource)` + 自动识别方言，不保存输入快照；
  - `FlowExecutionJpaRepository(DataSource dataSource, JdbcLogRepositoryDialect dialect)`：显式方言，不保存输入快照；
  - `FlowExecutionJpaRepository(DataSource dataSource, InputContextSerializer serializer)`：自动识别方言 + 可选输入快照；
  - `FlowExecutionJpaRepository(JdbcTemplate jdbcTemplate, JdbcLogRepositoryDialect dialect, InputContextSerializer serializer)`：完整注入。
- 每个接口方法均为单条 SQL（INSERT / UPDATE / SELECT），**不使用 `@Transactional`**，避免对 Spring 事务上下文的隐式要求；如需多操作事务由使用方自行包裹。

### D5：建表由使用方显式调用

`LogSchemaInitializer`（对应原 `DatabaseInitializer`）：普通工具类，构造器接受 `DataSource`（自动识别方言）或 `DataSource + JdbcLogRepositoryDialect`；`initialize()` 方法执行三张表 + 两个索引的 DDL，全部 SQL 来自方言。**无 `@PostConstruct`、无配置开关、不自动执行**；使用方在应用启动时自行调用。单条 DDL 失败记 WARN 并继续后续语句，不抛出（沿用现状容错语义）。

```java
new LogSchemaInitializer(dataSource).initialize();
```

### D6：无 AutoConfiguration，全部手动构造

模块**不包含任何 AutoConfiguration / spring.factories / AutoConfiguration.imports**，使用方式（Spring Boot 与其他项目相同）：

```java
DataSource dataSource = ...;
new LogSchemaInitializer(dataSource).initialize();          // 可选：首次建表

FlowExecutionJpaRepository repository = new FlowExecutionJpaRepository(dataSource);
Flow flow = new Flow();
flow.addListener(new PersistentExecutionListener(repository));
// 或一步：flow.addListener(new PersistentExecutionListener(dataSource));
```

- `PersistentExecutionListener` 搬到 `io.github.aimi.rag.workflow.log`，回调逻辑零改动；保留 `(FlowExecutionRepository)` 与 `(DataSource)` 两个构造器，后者内部自动识别方言创建 `FlowExecutionJpaRepository`；`flowName` 沿用默认 `unnamed-flow`（需求方确认不新增 `aimi.rag.log.flow-name` 配置）。
- 需要自定义持久化时，使用方自行实现 core 的 `FlowExecutionRepository` 并传入 listener，无需任何条件装配机制。
- 注意：`PersistentExecutionListener(DataSource)` 路径不自动建表，需先调用 `LogSchemaInitializer` 或自行执行 DDL。

### D7：input_context 默认不保存，可选精简快照（需求方确认）

不再默认执行 `ObjectMapper.writeValueAsString(context.getAll())`。完整 Context 可能包含 messages、文档正文、检索结果、大 DTO 或敏感数据，既会放大日志库，也不都是排查所需。

- `FlowExecutionJpaRepository` 默认使用 `InputContextSerializer.none()`，`input_context` 写 `null`。
- 抽象 `InputContextSerializer`：`String serialize(FlowContext context)`，由使用方决定保存哪些输入。
- 提供 `JacksonInputContextSerializer`：构造时指定 `includeKeys` 与 `maxChars`（默认 16 KB），只从 `context.getAll()` 挑选存在且被显式包含的 key；value 不可序列化时跳过该 key 并 WARN，不影响其他 key。
- 快照超过 `maxChars` 时写入 `{"truncated":true}` 并 WARN，不保存半截 JSON。
- 仓储调用序列化器时捕获运行异常，记 WARN 并按 `null` 写入，日志快照失败 MUST NOT 阻断 Flow。
- 保留 `input_context` 列以兼容表结构；该列只用于辅助复现输入相关问题，Flow/Step 状态与错误堆栈仍是主要排查信息。
- 执行日志本身仍会随执行次数增长，生产清理由使用方按 `start_time` 制定保留策略，模块不自动删除历史记录。

## Risks / Trade-offs

- [手写 SQL 的方言覆盖不全，非主流数据库可能建表失败] → 提供 `GenericLogRepositoryDialect` 兜底；建表为显式调用，用户可选择不调用 `LogSchemaInitializer` 而手工执行 DDL；方言单测覆盖六种数据库的 DDL 断言。
- [三个 `*JpaRepository` 虽保留类名，但包位置与形态（接口→普通 class）变化，外部若按旧包名注入会编译失败] → proposal 中已标注 BREAKING；本仓库内仅 log 模块自引用，无其他使用方；发布说明中列出迁移路径（改注入 `FlowExecutionRepository` 或更新包名）。
- [core 删除 `PersistentExecutionListener` 后，只用 core 不用 log 模块的用户失去该类] → 这是预期行为：持久化监听器本就属于持久化模块；core 保留 `FlowExecutionListener` 接口与 `FlowExecutionRepository` 契约，用户可自实现。
- [`Instant` 与 JDBC `Timestamp` 的时区处理在不同数据库上表现不一] → 统一用 `PreparedStatement.setObject(Instant)`（JDBC 4.2）并在 H2/MySQL/PostgreSQL 上做读写一致性测试；必要时在方言中提供 `toJdbcTimestamp(Instant)` 钩子。
- [spring-jdbc 没有实体脏检查，`updateExecutionStatus` 需显式 UPDATE] → 直接 `UPDATE flow_execution SET status=?, end_time=? WHERE id=?`，比 JPA 的 find-then-save 更简。

## Migration Plan

1. 新增 `aimi-rag-log` 模块并通过测试（H2 单测 + 方言矩阵断言）。
2. 根 pom 摘除 `aimi-rag-log-jpa` / `aimi-rag-log-mybatis`，登记 `aimi-rag-log`；删除 `mybatis-spring-boot-starter` 依赖管理。
3. core 删除 `PersistentExecutionListener`；全仓库检索旧 import 并替换。
4. 删除两个旧模块目录；`mvn clean install` 全量验证。
5. 数据层面无需迁移（表结构不变）；回滚 = 还原 pom 与模块目录、恢复 core 中的 listener。

## Open Questions

（已全部与需求方确认，无遗留问题）

- Q1：装配方式 → **已决**：模块为纯 Java 库，**不提供任何 AutoConfiguration / 自动注入 / 配置项**（workflow 并非只在 Spring Boot 项目使用），所有对象手动构造；建表显式调用 `LogSchemaInitializer`。
- Q2：是否扩展 `FlowExecutionRepository` 增加单条执行记录查询 → **已决**：不扩接口，保持能力对等。
- Q3：是否支持 `aimi.rag.log.flow-name` 配置 → **已决**：不支持，listener 沿用构造器传入 / 默认 `unnamed-flow`。
- Q4：三个 `*JpaRepository` 的命名 → **已决**：保留原类名，改为普通 class，包迁至 `io.github.aimi.rag.workflow.log.repository`，内部用 `JdbcTemplate` + `JdbcLogRepositoryDialect` 实现。
- Q5：`input_context` 是否保存完整 Context → **已决**：默认写 `null`；提供可选 `InputContextSerializer`，Jackson 实现只保存指定 key 并限制大小，避免日志膨胀与无关/敏感数据落库。
- Q6：方言与产品识别方式 → **已决**：尽可能参考 `JdbcChatMemoryRepositoryDialect`，接口直接提供 SQL、静态 `from(DataSource)` 按 JDBC URL 识别、显式方言优先；未知产品与元数据异常不默认 PostgreSQL。
