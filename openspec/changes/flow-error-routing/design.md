# 设计文档：Flow 级别错误路由

## 1. 设计目标

为 Flow 引擎的每个 Step 提供可编排的错误出口：Step 失败（重试耗尽）后不再只能终止
流程，而是可以路由到用户指定的错误处理节点（补偿 / 告警 / 日志 / 降级），随后流程
按图继续流转。

## 2. 核心设计

### 2.1 错误边（FlowEdge.ERROR）

```
FlowEdge
  ├── status = "*"      正常顺序流转
  ├── status = 自定义     decider 条件边
  ├── status = "LOOP"    循环回边（已有）
  └── status = "ERROR"   错误路由边（新增）
```

- `ERROR` 为保留状态，正常路由 `findNextNode()` 只匹配 decider 结果与 `*`，
  永远不会选中 ERROR 边
- 只有节点执行失败时，由 `findErrorNode()` 显式查找 ERROR 边

### 2.2 错误信息载体（StepErrorInfo）

```
StepErrorInfo (core.model)
  ├── CONTEXT_KEY = "flow.step.error"   写入 FlowContext 的固定 key
  ├── stepName: String                  失败的 Step 名称
  ├── errorMessage: String              异常消息
  ├── error: Throwable                  原始异常（StepExecuteException）
  └── timestamp: Instant                失败时间
```

路由到错误处理节点前写入 `context.set(StepErrorInfo.CONTEXT_KEY, info)`，
错误处理 Step 通过自身 `resolveInput()` 从 context 读取，保持 Step 自管理
输入输出的既有约定。

### 2.3 DSL（FlowBuilder）

```java
FlowBuilder.create()
    .start()
    .step(parseStep)
        .onFailure(logErrorStep)   // parseStep 失败 → logErrorStep
    .step(embeddingStep)
        .onFailure(alertStep)      // embeddingStep 失败 → alertStep
        .onFailureToEnd()          // （二选一）失败 → 直接到 END
    .step(storageStep)
    .end()
    .build();
```

规则：

- `onFailure(step)` / `onFailureToEnd()` 只能跟在 Step 节点之后，
  否则抛 `FlowValidationException`
- 调用后 `currentNode` 不变：链式后续的 `.step(C)` 仍接在当前 Step 的正常
  路径上；错误路径的延续通过 `from(errorStep).step(...)` 显式表达
- 同一 Step 可挂多个错误出口时，取图中第一条 ERROR 边（不推荐使用）

### 2.4 执行引擎（Flow）

主循环失败处理：

```
current.execute(context)
  ├─ 抛出 StepExecuteException（重试耗尽）
  │     ├─ 有 ERROR 边 → 写 StepErrorInfo → notifyStepFailure → current = 错误节点，继续
  │     └─ 无 ERROR 边 → notifyStepFailure → 抛 FlowException（现状）
  └─ 返回 StepStatus.FAILED
        ├─ 有 ERROR 边 → 同上
        └─ 无 ERROR 边 → 抛 FlowException（现状）
```

并行分支 `executeBranch()` 内采用同样规则；分支内 Step 无错误路由时行为不变
（异常经 CompletableFuture 传播，整个并行失败）。

### 2.5 与既有机制的协作

| 机制 | 关系 |
|------|------|
| 重试 | 优先执行；重试耗尽才触发错误路由 |
| FlowExecutionListener | 路由前照常回调 onStepFailure（Step 确实失败）；不回调 onFlowFailure（流程未失败） |
| FlowValidator | ERROR 边是普通图边，可达性/孤立节点校验天然覆盖；经 ERROR 边形成的回环仍被循环检测拒绝 |
| 循环/并行 | loop 内 Step 可挂 onFailure；并行分支内 Step 的错误路由目标不能跨出并行区域（现有互斥校验拦截） |

## 3. 改动清单

| 文件 | 改动 |
|------|------|
| `core/flow/FlowEdge.java` | 新增 `ERROR` 常量、`isError()` |
| `core/model/StepErrorInfo.java` | 新增 |
| `core/flow/FlowBuilder.java` | 新增 `onFailure()` / `onFailureToEnd()` / `ensureCurrentIsStep()` |
| `core/flow/Flow.java` | 新增 `findErrorNode()`；主循环与 `executeBranch()` 增加错误路由 |
