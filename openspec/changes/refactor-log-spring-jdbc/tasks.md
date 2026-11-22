# 开发计划：refactor-log-spring-jdbc

> 前置：proposal / design / specs 需用户确认后再开始执行。
> 约束：模块为**纯 Java 库**——不依赖 Spring Boot、无 AutoConfiguration、无自动注入、无配置项；所有对象手动构造。

## 1. 新模块骨架

- [x] 1.1 新建 `aimi-rag-log/pom.xml`：parent 为根工程，依赖 `aimi-rag-core`、`spring-jdbc`（非 starter）、`jackson-databind`（可选输入快照实现）、`slf4j-api`；test 依赖 H2、JUnit
- [x] 1.2 根 `pom.xml`：`<modules>` 移除 `aimi-rag-log-jpa` / `aimi-rag-log-mybatis`、加入 `aimi-rag-log`；`dependencyManagement` 移除 `mybatis-spring-boot-starter`

## 2. 方言抽象（对应 spec: jdbc-log-persistence）

- [x] 2.1 `io.github.aimi.rag.workflow.log.dialect.JdbcLogRepositoryDialect` 接口参考 `JdbcChatMemoryRepositoryDialect`：直接提供五个 DML 与五个 DDL 的完整 SQL（`?` 占位），不暴露列类型拼接 API
- [x] 2.2 内置实现：`MysqlLogRepositoryDialect`（MySQL/MariaDB 共用）、`PostgresLogRepositoryDialect`、`H2LogRepositoryDialect`、`OracleLogRepositoryDialect`、`SqlServerLogRepositoryDialect`、`SqliteLogRepositoryDialect`、`GenericLogRepositoryDialect`
- [x] 2.3 静态 `from(DataSource)` 参考 Spring AI 按 `DatabaseMetaData.getURL()` 小写匹配 `postgresql` / `mysql` / `mariadb` / `sqlserver` / `h2` / `oracle` / `sqlite`；try-with-resources 关连接；未识别走 Generic 并 WARN，元数据异常抛 `IllegalStateException`；静态 `resolve` 实现显式方言优先与不匹配 WARN

## 3. 三个仓储类（保留原类名，普通 class）

- [x] 3.1 `log.repository.StepExecutionJpaRepository`：普通 class，`JdbcTemplate` + `JdbcLogRepositoryDialect` 实现 `step_execution` 表 INSERT 与按 executionId 查询（start_time 升序）
- [x] 3.2 `log.repository.StepFailureJpaRepository`：普通 class，实现 `step_failure` 表 INSERT
- [x] 3.3 `log.repository.FlowExecutionJpaRepository implements FlowExecutionRepository`：普通 class，负责 `flow_execution` 表读写并组合 3.1/3.2，实现接口五个方法；提供 `(DataSource)` / `(DataSource, JdbcLogRepositoryDialect)` / `(DataSource, InputContextSerializer)` / `(JdbcTemplate, JdbcLogRepositoryDialect, InputContextSerializer)` 构造器；单条 SQL 完成，不加 `@Transactional`
- [x] 3.4 `LogSchemaInitializer`：普通工具类，`(DataSource)` / `(DataSource, JdbcLogRepositoryDialect)` 构造器；`initialize()` 由方言生成并执行三表 + 两索引 DDL；仅显式调用时执行，无 `@PostConstruct`、无开关；单条失败 WARN 并继续
- [x] 3.5 `InputContextSerializer` + `JacksonInputContextSerializer`：默认 `none()` 写 null；Jackson 实现按 includeKeys 精简快照、默认最大 16 KB、超长写 `{"truncated":true}`、单值失败跳过；自定义序列化器异常由仓储捕获并写 null

## 4. Listener 迁移（对应 spec: execution-persistence-listener）

- [x] 4.1 将 `PersistentExecutionListener` 从 `aimi-rag-core` 的 `core.flow` 包移动到 `aimi-rag-log` 的 `log` 包，回调逻辑零改动，更新 javadoc 引用；保留 `FlowExecutionRepository` / `DataSource` 两个构造器，后者内部 `JdbcTemplate` + 方言解析创建 `FlowExecutionJpaRepository`，不自动建表
- [x] 4.2 删除 `aimi-rag-core` 中的旧 `PersistentExecutionListener`，全仓库检索并替换旧 import
- [x] 4.3 **不创建**任何 AutoConfiguration / `AutoConfiguration.imports` / `spring.factories`；旧模块中的 `flowListenerRegistrar`、`flowTablesBeforeEntityManagerFactory`、`aimi.rag.step-log-jdbc` / `aimi.rag.auto-init-tables` 等配置随旧模块一并删除

## 5. 测试（纯 JUnit + H2，无 Spring 上下文）

- [x] 5.1 方言矩阵单测：六种数据库 + 兜底方言的完整 DML/DDL 字符串断言（时间戳/文本/字符串类型、IF NOT EXISTS、upsert 行为）
- [x] 5.2 `JdbcLogRepositoryDialect` 识别单测：JDBC URL 自动识别（H2/MySQL/MariaDB/PostgreSQL 等）、未识别 Generic 兜底、元数据异常、显式传入优先与不匹配 WARN、连接关闭
- [x] 5.3 H2 集成测试：`new FlowExecutionJpaRepository(dataSource)` 五个接口方法的读写一致性（含 `Instant` 往返、getExecutionHistory 排序）
- [x] 5.4 输入快照测试：默认 input_context 为 null；Jackson 实现只保存 includeKeys；不可序列化 value 跳过；超长写截断标记；自定义序列化器异常写 null 且不阻断
- [x] 5.5 Listener 测试：手动 `new PersistentExecutionListener(repository)` + `flow.addListener` 后各生命周期回调写库正常；`DataSource` 构造器可用且不执行 DDL
- [x] 5.6 建表测试：显式 `initialize()` 建表成功；连续调用两次幂等；不调用时无 DDL

## 6. 旧模块清理与验证

- [x] 6.1 删除 `aimi-rag-log-jpa` / `aimi-rag-log-mybatis` 目录
- [x] 6.2 全仓库 grep 确认无 `log.jpa` / `log.mybatis` / `mybatis` / `step-log-jdbc` / `auto-init-tables` 残留引用（含 integration-test、resources、文档；OpenSpec 历史规格除外）
- [x] 6.3 `mvn clean install` 全量构建 + 测试通过（按用户要求排除既有的远程测试 `TestRemoteConn`：外部阿里云 API Key 返回 401，与本变更无关）
- [x] 6.4 更新 TECH_DEBT / README 中涉及 log 模块的描述（如有）

## 7. 开放问题（已全部与需求方确认）

- [x] 7.1 Q1：模块为纯 Java 库，**不提供任何 AutoConfiguration / 自动注入 / 配置项**，所有对象手动构造；建表显式调用 `LogSchemaInitializer`
- [x] 7.2 Q2：**不扩展** `FlowExecutionRepository` 接口，保持能力对等
- [x] 7.3 Q3：**不支持** `aimi.rag.log.flow-name` 配置，listener 沿用构造器 / 默认 `unnamed-flow`
- [x] 7.4 Q4：三个 `*JpaRepository` **保留原类名**，改普通 class，包迁至 `io.github.aimi.rag.workflow.log.repository`
- [x] 7.5 Q5：`input_context` **默认写 null**；提供可选 `InputContextSerializer`，Jackson 实现只保存指定 key 并限制大小，避免日志膨胀与无关/敏感数据落库
- [x] 7.6 Q6：方言尽可能参考 `JdbcChatMemoryRepositoryDialect`：完整 SQL 接口、JDBC URL 静态识别、显式方言优先；未知产品与元数据异常不默认 PostgreSQL
