## ADDED Requirements

### Requirement: 自动提供日志持久化 Bean

当应用引入 `aimi-rag-log-spring-boot-starter` 时，自动配置 SHALL 按数据源解析规则提供 `FlowExecutionRepository`、`PersistentExecutionListener` 与 `LogSchemaInitializer` Bean。

数据源解析规则：
- 配置了 `aimi.rag.log.data-source-bean-name` 时使用该名称对应的 `DataSource`；
- 未配置时使用唯一的 `DataSource`（存在多个时取 `@Primary`）；
- 多个 `DataSource`、未配置名称且无 `@Primary` 时不装配日志 Bean。

#### Scenario: 单个 DataSource 自动装配

- **WHEN** Spring 容器中存在唯一 `DataSource`，且业务未自定义日志相关 Bean
- **THEN** 容器中存在 `FlowExecutionRepository` Bean（实现为 `FlowExecutionJpaRepository`）
- **AND** 容器中存在 `PersistentExecutionListener` Bean
- **AND** 容器中存在 `LogSchemaInitializer` Bean

#### Scenario: 通过属性指定数据源

- **WHEN** 容器中存在多个 `DataSource`，且配置 `aimi.rag.log.data-source-bean-name=logDataSource`
- **THEN** 日志相关 Bean 使用名为 `logDataSource` 的 `DataSource`
- **AND** 应用正常启动

#### Scenario: 多数据源无主且未指定时跳过装配

- **WHEN** 容器中存在多个 `DataSource`，未配置 `data-source-bean-name` 且没有 `@Primary`
- **THEN** 自动配置不创建日志仓储、监听器与建表工具 Bean
- **AND** 应用仍可正常启动

#### Scenario: 业务自定义仓储时自动退让

- **WHEN** 业务方自行声明了 `FlowExecutionRepository` Bean
- **THEN** 自动配置不再创建默认的 `FlowExecutionRepository`
- **AND** `PersistentExecutionListener` 使用业务方提供的仓储

### Requirement: 自动将监听器挂载到所有 Spring 管理的 Flow Bean

自动配置 SHALL 将所有 `FlowExecutionListener` Bean 自动挂载到容器中的每个 `Flow` Bean，业务代码无需调用 `flow.addListener(...)`。

#### Scenario: Flow Bean 自动获得持久化监听器

- **WHEN** 数据源可解析，且应用声明了一个 `Flow` Bean
- **THEN** 该 `Flow` 的监听器列表包含 `PersistentExecutionListener`
- **AND** 业务代码未调用 `flow.addListener(...)`

#### Scenario: 自定义监听器同样自动挂载

- **WHEN** 应用声明了自定义 `FlowExecutionListener` Bean
- **THEN** 该监听器与该 `Flow` 的其余监听器一起被自动挂载

#### Scenario: 非 Bean 的 Flow 不受影响

- **WHEN** 应用在业务方法中直接创建 `Flow` 实例（未注册为 Spring Bean）
- **THEN** 该实例不会被自动挂载监听器，行为与未引入 starter 时一致

### Requirement: 可配置的自动建表

自动配置 SHALL 通过 `aimi.rag.log.initialize-schema` 控制启动时是否建表，默认值为 `true`。

#### Scenario: 默认启动时建表

- **WHEN** 使用默认配置启动应用
- **THEN** starter 调用 `LogSchemaInitializer.initialize()` 创建日志表与索引
- **AND** 随后执行 `Flow` Bean 时日志可正常写入

#### Scenario: 关闭自动建表

- **WHEN** 配置 `aimi.rag.log.initialize-schema=false`
- **THEN** starter 在启动阶段不执行任何 `CREATE TABLE` 或索引创建语句
- **AND** `LogSchemaInitializer` Bean 仍然可用，供业务方按需调用

#### Scenario: 建表语句失败不阻断启动

- **WHEN** 某条建表或索引语句执行失败（例如表已存在且方言不支持 `IF NOT EXISTS`）
- **THEN** starter 记录 WARN 并继续执行剩余语句
- **AND** 应用启动不被中断

### Requirement: 支持关闭自动配置

自动配置 SHALL 支持通过 `aimi.rag.log.enabled=false` 整体关闭，关闭后不创建任何日志 Bean、也不挂载监听器。

#### Scenario: 通过属性关闭

- **WHEN** 配置 `aimi.rag.log.enabled=false`
- **THEN** 容器中不存在 `PersistentExecutionListener` Bean
- **AND** `Flow` Bean 的监听器列表为空（假设无其他监听器 Bean）
