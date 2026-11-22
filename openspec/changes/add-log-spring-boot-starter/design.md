## Context

`aimi-rag-core` 的 `Flow` 自身持有 `FlowExecutionListener` 列表，注册入口只有 `Flow.addListener(...)`；`aimi-rag-log` 按约定是纯 Java 库，不提供任何自动装配，因此业务方需要对每个 `Flow` 手动创建并挂载 `PersistentExecutionListener`（见 `TECH_DEBT.md` W-007）。

当前仓库基于 Spring Boot 3.x（根 `pom.xml` 通过 `spring-boot-dependencies` 管理版本），已存在 `refactor-log-spring-jdbc` 与 `execution-metadata` 等 OpenSpec 变更，`aimi-rag-log` 的仓储、方言、建表工具均已稳定。

本设计只解决“Spring Boot 应用中省略 `flow.addListener`”，不改变 `aimi-rag-core` / `aimi-rag-log` 的现有 API 与行为。

## Goals / Non-Goals

**Goals:**

- Spring Boot 应用引入 starter 后，业务代码不再出现 `flow.addListener(...)`。
- 自动提供 `FlowExecutionRepository` 与 `PersistentExecutionListener`。
- 保持 `aimi-rag-log` 纯 Java，Boot 相关依赖只出现在新 starter 模块。
- 所有自动装配 Bean 可被业务方覆盖；支持整体关闭。
- 多数据源场景下可显式指定数据源；未指定且无 `@Primary` 时不静默选错。

**Non-Goals:**

- 不为 `flow_name`、输入快照等新增配置项。
- 不支持非 Spring 容器管理的 `Flow`（局部 `new` 或工厂临时创建）。
- 不实现按 Flow 选择监听器、异步写日志、失败降级等增强能力。

## Decisions

### 1. 新建独立 starter 模块，而不是改 `aimi-rag-core` / `aimi-rag-log`

新增 `aimi-rag-log-spring-boot-starter`，依赖 `aimi-rag-log` 与 `spring-boot-autoconfigure`。`aimi-rag-core`、`aimi-rag-log` 不引入任何 Boot 依赖。

备选：在 `Flow` 中加入全局静态监听器注册表。实现最省，但引入全局可变状态，测试互相污染，多租户/多实例无法隔离，否决。

### 2. 用 `BeanPostProcessor` 自动挂载，而不是改 `FlowBuilder`

`FlowExecutionListenerRegistrar` 实现 `BeanPostProcessor`，在 `postProcessAfterInitialization` 中判断 `bean instanceof Flow`，把容器内所有 `FlowExecutionListener` 挂到该 `Flow` 上。监听器通过 `ObjectProvider<FlowExecutionListener>` 延迟获取，避免 BPP 注册阶段提前实例化业务 Bean。

备选：
- 修改 `FlowBuilder` 支持构建期注入监听器：需要改动 core API，且只覆盖走 `FlowBuilder` 的路径；非 Spring 用户仍需自行传参。
- `SmartInitializingSingleton`：只能覆盖启动期已创建的单例 Bean，遗漏 prototype 或运行期创建的 `Flow`。

### 3. Bean 装配条件与数据源选择

- `LogPersistenceAutoConfiguration` 使用 `@AutoConfiguration(after = DataSourceAutoConfiguration.class)`。
- 新增 `LogPersistenceProperties`（`@ConfigurationProperties(prefix = "aimi.rag.log")`），包含 `initializeSchema`（默认 `true`）与 `dataSourceBeanName`（默认 `null`）。
- 新增自定义 `LogDataSourceCondition`，作用于日志相关 Bean 方法：
  - 配置了 `data-source-bean-name` 时，仅当该名称对应一个 `DataSource` Bean 才匹配；
  - 未配置时，存在唯一 `DataSource`（含 `@Primary` 时选主）才匹配；
  - 多个 `DataSource` 且未指定名称、也无 `@Primary` 时不匹配，应用正常启动但日志能力不装配。
- Bean 方法通过 `ApplicationContext` / `BeanFactory` 按上述规则解析 `DataSource`，避免条件与实际取值不一致。
- 所有 Bean 使用 `@ConditionalOnMissingBean`，业务方可自定义仓储或监听器覆盖。
- `aimi.rag.log.enabled=false`（`matchIfMissing = true`）整体关闭自动配置。

### 4. 挂载容器内全部 `FlowExecutionListener` Bean

BPP 使用 `ObjectProvider.orderedStream()` 挂载全部监听器 Bean，而非只挂 `PersistentExecutionListener`。这样业务方新增自定义监听器时同样免注册，保持组合性。

代价是无法按 Flow 选择性挂载；需要该能力时可后续引入注解或标记接口。当前通过“不引入 starter”或 `aimi.rag.log.enabled=false` 整体退出。

### 5. 可配置的自动建表

starter 提供 `aimi.rag.log.initialize-schema`（默认 `true`）。开启时，在启动阶段创建并调用 `LogSchemaInitializer.initialize()`；关闭时不执行任何 DDL，表由业务方通过 `LogSchemaInitializer` Bean 或自有 DDL 管理。

`LogSchemaInitializer.initialize()` 逐条执行建表/索引语句，单条失败仅 WARN 并继续，因此不会因已存在的表或权限问题阻断启动；支持 `IF NOT EXISTS` 的方言可重复执行，不支持的方言会记录 WARN。

默认开启的原因：监听器会被自动挂载到所有 `Flow` Bean，若表不存在会直接中断 Flow，零配置场景下默认建表更符合预期；同时保留开关供生产环境显式管理 DDL。

### 6. 自动配置注册方式

使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `LogPersistenceAutoConfiguration`，而非旧版 `spring.factories`。

## Risks / Trade-offs

- [自动建表默认开启，可能掩盖表结构问题或在不支持 `IF NOT EXISTS` 的库上产生 WARN] → 单条失败仅 WARN、不阻断启动；可用 `aimi.rag.log.initialize-schema=false` 关闭并改由业务方管理 DDL。
- [所有监听器被挂到所有 Spring 管理的 Flow Bean，可能产生意料之外的横切行为] → 需求已确认“一律挂载”；仅引入 starter 时生效，提供整体开关。
- [多数据源且未指定 `data-source-bean-name`、也无 `@Primary` 时日志能力静默缺失] → 设计为显式跳过而非猜测；文档说明配置方式。
- [BPP 通过 `ObjectProvider` 触发监听器 Bean 提前创建，若监听器依赖 `Flow` 可能形成循环] → 监听器仅依赖仓储/数据源，当前实现无循环；后续新增监听器需避免反向依赖 `Flow`。
- [同一 `Flow` 实例被重复后处理导致监听器重复挂载] → Spring 对单个 Bean 实例只调用一次 `postProcessAfterInitialization`；若后续出现代理/重复初始化场景，再引入幂等保护。

## Migration Plan

1. 业务应用引入 `aimi-rag-log-spring-boot-starter` 依赖。
2. 删除业务代码中的 `flow.addListener(...)`。
3. 默认启动即建表；若生产环境自行管理 DDL，设置 `aimi.rag.log.initialize-schema=false`。
4. 多数据源时设置 `aimi.rag.log.data-source-bean-name`，或标记 `@Primary`。
5. 如需回滚，移除 starter 依赖并恢复手动挂载即可；`aimi-rag-log` 数据与表结构不受影响。

## Open Questions

- `initialize-schema=true` 且建表失败时，是否需要在启动阶段 fail-fast（当前 `LogSchemaInitializer` 仅 WARN）？
