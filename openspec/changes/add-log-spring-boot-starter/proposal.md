## Why

使用 `aimi-rag-log` 时，业务方必须为每个 `Flow` 手动调用 `flow.addListener(...)`，多 Flow 场景重复且容易遗漏；`aimi-rag-log` 又按约定保持纯 Java、不做自动装配。新增一个独立的 Spring Boot Starter，把仓储、监听器与挂载动作收敛到自动配置中，业务代码即可完全省略 `flow.addListener`。

## What Changes

- 新增 `aimi-rag-log-spring-boot-starter` 模块，依赖 `aimi-rag-log` 与 `spring-boot-autoconfigure`；`aimi-rag-log` 保持纯 Java 库不变。
- 新增 `LogPersistenceAutoConfiguration`：
  - 自动解析单个 `DataSource`（含 `@Primary`），或通过 `aimi.rag.log.data-source-bean-name` 指定数据源，自动提供 `FlowExecutionRepository`（`FlowExecutionJpaRepository`）、`PersistentExecutionListener` 与 `LogSchemaInitializer`；
  - 所有 Bean 使用 `@ConditionalOnMissingBean`，允许业务方覆盖；
  - `aimi.rag.log.enabled=false` 可整体关闭自动配置。
- 新增 `FlowExecutionListenerRegistrar`（`BeanPostProcessor`）：把容器中所有 `FlowExecutionListener` bean 自动挂载到所有 `Flow` bean；只要是 Spring 管理的 `Flow` 一律挂载。
- 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动配置。
- 新增配置项：
  - `aimi.rag.log.initialize-schema`（默认 `true`）：启动时调用 `LogSchemaInitializer.initialize()` 建表；
  - `aimi.rag.log.data-source-bean-name`（可选）：多数据源时显式指定使用的 `DataSource` bean 名称；
  - `flow_name` 与输入快照配置暂不提供。

## Capabilities

### New Capabilities

- `log-spring-boot-starter`: Spring Boot 自动装配 `aimi-rag-log` 的仓储与监听器，并将监听器自动挂载到 `Flow` bean，免除业务侧 `flow.addListener`。

### Modified Capabilities

无。`aimi-rag-log` 的 API 与行为不变。

## Impact

- 新增模块 `aimi-rag-log-spring-boot-starter`，并加入根 `pom.xml` 的 `<modules>`。
- 新增对 `spring-boot-autoconfigure` 的依赖，仅存在于 starter，不反向引入 `aimi-rag-log` / `aimi-rag-core`。
- 自动挂载仅对 Spring 容器管理的 `Flow` bean 生效；在业务代码中临时 `new` 或通过工厂创建的非 bean `Flow` 仍需手动挂载。
- `initialize-schema` 默认开启，启动时会执行建表语句；不需要自动建表时可设为 `false`，改为手工执行 `LogSchemaInitializer` 或自有 DDL。
- 多 `DataSource` 时优先使用 `aimi.rag.log.data-source-bean-name` 指定的数据源；未指定且无 `@Primary` 时不装配，避免选错数据源。
