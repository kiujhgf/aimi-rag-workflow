# Proposal: Flow 级别错误路由

## 背景

当前 Flow 引擎中，Step 失败后的处理手段只有重试（retryTimes / retryableExceptions）。
重试耗尽后 `AbstractStep.process()` 抛出 `StepExecuteException`，`Flow.execute()` 直接
将其包装为 `FlowException` 并终止整个流程：

1. 无法为某个 Step 指定失败后的自定义处理路径（补偿、告警、降级、记录日志）
2. 无法做到「单点失败不影响整体流程」（失败即终止）
3. 错误信息只通过异常传播，错误处理逻辑无法从 FlowContext 中结构化的获取失败详情

## 目标

在 `aimi-rag-core` 中为每个 Step 支持 Flow 级别的错误路由：

- FlowBuilder 提供 `onFailure(Step)` / `onFailureToEnd()` DSL，为 Step 绑定错误出口
- Step 失败（重试耗尽抛异常，或返回 FAILED 状态）时，若配置了错误路由则流转到
  错误处理节点，流程继续；否则保持现有行为（抛出 FlowException 终止流程）
- 路由前将结构化的错误信息 `StepErrorInfo` 写入 FlowContext，供错误处理 Step
  通过自身 InputResolver 消费
- 主流程与并行分支（executeBranch）行为一致

## 范围

### 包含
- `FlowEdge` 新增 `ERROR` 保留状态与 `isError()`
- 新增 `StepErrorInfo` 模型（stepName / errorMessage / error / timestamp）
- `FlowBuilder` 新增 `onFailure(Step)`、`onFailureToEnd()` DSL
- `Flow.execute()` 主循环错误路由（StepExecuteException 与 FAILED 状态两条路径）
- `Flow.executeBranch()` 并行分支内错误路由
- 单元测试

### 不包含
- Step 内部 ErrorHandler（方案一，降级返回默认值）——如需要另立变更
- 错误处理节点失败后的二级路由策略配置（当前自然支持：错误处理节点自身也可挂 onFailure）
- 流程级全局兜底错误处理器（global onFailure）
- 经 ERROR 边形成的回环（如错误处理后重试原 Step）——本期仍被循环检测拒绝
- decider 节点决策异常的错误路由

## 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 错误边表示 | FlowEdge 保留状态 `ERROR` | 与 LOOP 边一致的做法，正常路由只认 decider 结果与 `*`，ERROR 边不会被正常流转误中 |
| 错误信息传递 | `StepErrorInfo` 写入 FlowContext 固定 key | 错误处理 Step 可通过 resolveInput 自取，符合 Step 自管理输入输出的约定 |
| DSL 链式语义 | `onFailure()` 后 currentNode 不变 | `.step(A).onFailure(B).step(C)` 中 C 仍接在 A 的正常路径上，错误路径用 `from(B)` 显式延续 |
| 未配置错误路由 | 保持现状抛 FlowException 终止 | 向后兼容，不改变现有流程行为 |
| 错误路由触发时机 | 重试耗尽之后 | 错误路由是最终兜底，不替代重试 |
| 监听器通知 | 错误路由前仍回调 onStepFailure | Step 确实失败了，持久化/监控应如实记录；但不触发 onFlowFailure（流程未失败） |

## 影响评估

- **aimi-rag-core**：修改 `FlowEdge` / `FlowBuilder` / `Flow`，新增 `StepErrorInfo`
- **其他模块**：无影响（纯新增能力，未配置 onFailure 的流程行为不变）

## 风险

| 风险 | 缓解措施 |
|------|---------|
| ERROR 边参与可达性校验，错误处理节点悬空导致校验失败 | 错误边即正常图边，BFS 可达性天然覆盖；错误处理节点必须能到达 END 由现有校验保证 |
| 错误处理节点自身失败造成递归兜底 | 允许其继续挂 onFailure，无路由时终止，行为可预期 |
| 并行分支内错误路由到区域外节点 | 由现有并行互斥校验拦截，不新增规则 |
