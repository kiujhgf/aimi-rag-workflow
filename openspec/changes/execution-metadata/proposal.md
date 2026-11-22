# Proposal: 执行元数据（executionId 生成上移 + 当前 Step 追踪）

## 背景

当前 `executionId` 的生成发生在 `PersistentExecutionListener.onFlowStart` 中，依赖
`FlowExecutionRepository.createExecution()`（内部 `UUID.randomUUID()` 并落库）。由此带来两个问题：

1. **不挂持久化监听器就没有执行 ID**：`flow.execute(context)` 本身不产生任何执行标识，
   只有显式注册 `PersistentExecutionListener`（并注入 Repository）才会生成 `executionId`。
   纯内存执行、或需要跨 Step 追踪的轻量场景，context 里拿不到 ID。
2. **ID 生成与持久化耦合**：`createExecution` 同时承担「生成 ID」与「落库」两个职责，
   且 Repository 各方法的 `executionId` 参数在 context 已持有该 ID 的情况下显得冗余。

同时，缺少「当前正在执行的 Step 名称」这一通用元数据，无法在错误处理/日志中简单获知
执行到哪个节点。

## 目标

1. 将 `executionId` 的生成上移到 `Flow.execute()`：每次执行无条件在 context 写入一个
   执行 ID（默认 `UUID`），不依赖任何持久化实现。
2. 简化 `FlowExecutionRepository` 接口：所有方法不再显式接收 `executionId` 参数，
   统一从 `FlowContext` 读取；`StepExecutionRecord` / `StepFailureRecord` 不再承载
   `executionId` 字段。
3. 新增 `__current_step`：在 `AbstractStep.process()` 通用入口写入当前 Step 名称。

## 范围

### 包含
- 新增 `FlowContextKeys` 常量类，统一管理 `__execution_id` / `__current_step` 等元数据 key
- `Flow.execute()` 中通过 `setIfAbsent` 生成并写入 `executionId`
- `FlowExecutionRepository` 接口方法签名调整（去掉 `executionId` 参数，改为从 context 读）
- `StepExecutionRecord` / `StepFailureRecord` 移除 `executionId` 字段
- `PersistentExecutionListener` 适配（不再自行生成 ID）
- `JpaFlowExecutionRepository` / `MyBatisFlowExecutionRepository` 同步适配
- `AbstractStep.process()` 写入 `__current_step`
- 单元测试

### 不包含
- traceId / flowName / 入参快照等其他元数据（本次只做 executionId + currentStep）
- `getExecutionHistory` 的查询语义变更（保持显式 `executionId` 参数，因调用方无 context）
- decider 节点的 `__current_step` 覆盖（decider 不经过 AbstractStep）

## 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| executionId 生成位置 | `Flow.execute()`，`setIfAbsent` | 不依赖 DB；context 已有 ID 则保留，支持外部传入 traceId / 幂等键 |
| Repository 获取 ID 方式 | 所有方法从 `FlowContext` 读，去掉 `executionId` 参数 | executionId 已在 context，传参冗余且易不一致 |
| `createExecution` 返回值 | `void`（原 `String`） | ID 已由 execute 生成并写入 context，无需返回 |
| record 的 `executionId` 字段 | 从 `StepExecutionRecord` / `StepFailureRecord` 移除 | 写入路径不再需要；查询返回时调用方已知 executionId |
| key 常量位置 | 新建 `FlowContextKeys` | 集中管理元数据 key，避免散落在 listener / step / repository |
| `__current_step` 写入位置 | `AbstractStep.process()`，仅 Step | 所有 Step 的通用执行入口；decider 不经过 AbstractStep，不做覆盖 |
| `getExecutionHistory` | 保留 `executionId` 参数 | 事后查询场景，调用方持有 ID 而非 context |

## 影响评估

| 模块 | 影响 |
|------|------|
| aimi-rag-core | 新增 `FlowContextKeys`；改 `Flow.execute` / `FlowExecutionRepository` / `PersistentExecutionListener` / `AbstractStep` / `StepExecutionRecord` / `StepFailureRecord` |
| aimi-rag-log-jpa | 改 `JpaFlowExecutionRepository` 实现 |
| aimi-rag-log-mybatis | 改 `MyBatisFlowExecutionRepository` 实现 |

## 风险

| 风险 | 缓解措施 |
|------|---------|
| Repository 接口 + record 字段 breaking，波及 3 个模块 | 同一 change 内一并修改，编译 + 测试兜底 |
| decider 不更新 `__current_step` | 文档说明局限，decider 通常瞬时且可读 StepErrorInfo 定位 |
| 并行分支的 `__current_step` 只存在于分支 subContext | 主 context 仅在 ParallelStart/End 阶段反映，符合分支隔离语义 |
| `createExecution` 改为 void 后，旧调用方依赖返回值 | 唯一调用方是 PersistentExecutionListener，同步改造即可 |
