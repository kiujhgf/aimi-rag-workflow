## 1. 模块与依赖

- [x] 1.1 新建 `aimi-rag-log-spring-boot-starter` 模块目录与 `pom.xml`，依赖 `aimi-rag-log`、`spring-boot-autoconfigure`，测试依赖 `spring-boot-starter-test`、H2
- [x] 1.2 在根 `pom.xml` 的 `<modules>` 中加入新模块
- [x] 1.3 确认 `aimi-rag-core` / `aimi-rag-log` 未新增任何 Spring Boot 依赖

## 2. 配置项与数据源解析

- [x] 2.1 新增 `LogPersistenceProperties`：`initializeSchema`（默认 `true`）、`dataSourceBeanName`（默认 `null`），通过 `@EnableConfigurationProperties` 注册
- [x] 2.2 新增 `LogDataSourceCondition`：优先按 `data-source-bean-name` 匹配，否则要求唯一 `DataSource` 或 `@Primary`
- [x] 2.3 新增数据源解析工具，保证条件判断与 Bean 创建使用同一个 `DataSource`

## 3. 自动配置实现

- [x] 3.1 新增 `LogPersistenceAutoConfiguration`：`@AutoConfiguration(after = DataSourceAutoConfiguration.class)`、`@ConditionalOnProperty(aimi.rag.log.enabled, matchIfMissing = true)`
- [x] 3.2 提供 `FlowExecutionRepository`（`FlowExecutionJpaRepository`）与 `PersistentExecutionListener`，均加 `@ConditionalOnMissingBean` 与 `@Conditional(LogDataSourceCondition.class)`
- [x] 3.3 提供 `LogSchemaInitializer` Bean；`initialize-schema=true`（默认）时在启动阶段调用 `initialize()`
- [x] 3.4 新增 `FlowExecutionListenerRegistrar`（`BeanPostProcessor`），通过 `ObjectProvider<FlowExecutionListener>` 把所有监听器 Bean 挂到所有 `Flow` Bean
- [x] 3.5 新增 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动配置
- [x] 3.6 为自动配置补充 JavaDoc，说明仅对 Spring Bean 生效、自动建表开关、多数据源指定方式与整体关闭开关

## 4. 测试

- [x] 4.1 单 `DataSource` + `Flow` Bean：断言自动创建仓储/监听器/建表工具，且 Flow 监听器列表包含 `PersistentExecutionListener`
- [x] 4.2 自定义 `FlowExecutionListener` Bean：断言同样被自动挂载
- [x] 4.3 多 `DataSource` + `data-source-bean-name`：断言使用指定数据源并正常装配
- [x] 4.4 多 `DataSource` 未指定且无 `@Primary`、无 `DataSource`：断言不装配日志 Bean，`Flow` 监听器为空
- [x] 4.5 业务自定义 `FlowExecutionRepository`：断言默认仓储退让，监听器使用自定义仓储
- [x] 4.6 `aimi.rag.log.enabled=false`：断言不创建监听器、不挂载
- [x] 4.7 默认 `initialize-schema=true`：断言表被创建，执行 Flow 后 `flow_execution` 记录状态为 `COMPLETED`
- [x] 4.8 `initialize-schema=false`：断言启动不执行 DDL，且 `LogSchemaInitializer` Bean 仍可注入

## 5. 验证与收尾

- [x] 5.1 执行 `mvn -pl aimi-rag-log-spring-boot-starter -am test`，全部用例通过
- [x] 5.2 更新 `TECH_DEBT.md` W-007 状态与实现说明
