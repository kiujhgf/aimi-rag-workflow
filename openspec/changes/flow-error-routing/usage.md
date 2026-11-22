# Flow 错误路由 DSL 使用说明

`FlowBuilder` 支持为每个 Step 绑定失败后的自定义出口：Step 重试耗尽或返回
`FAILED` 后，不再只能终止整个流程，而是可以路由到指定的错误处理节点。

## 1. DSL 方法

| 方法 | 说明 |
|------|------|
| `onFailure(Step<?, ?> errorStep)` | 当前 Step 失败时路由到 `errorStep` |
| `onFailureToEnd()` | 当前 Step 失败时直接路由到 END，流程正常结束 |
| `onFailureToLoopStart()` | 循环体内：当前 Step 失败时回到循环判定节点，跳过本次迭代剩余步骤 |
| `onFailureToLoopStart(Step<?, ?> errorHandler)` | 循环体内：当前 Step 失败时先执行 `errorHandler`，再回到循环判定节点 |

使用约束：

- 只能紧跟在 `.step(...)` 之后调用；在 `decider(...)` 之后或无当前节点时调用会抛
  `FlowValidationException`
- `onFailureToLoopStart(...)` 还要求处于 `loop()` / `endLoop()` 之间，否则抛
  `FlowValidationException`
- 调用后当前节点（currentNode）**不变**，后续链式调用仍接在该 Step 的**正常路径**上
- 一个 Step 只应绑定一个错误出口；绑定多个时运行时取图中第一条 ERROR 边（不推荐）

## 2. 触发时机

Step 的失败分两种，均在**重试耗尽之后**触发错误路由：

1. `process()` 抛出 `StepExecuteException`（`doProcess` 异常且超过 retryTimes）
2. `process()` 返回 `StepStatus.FAILED`

Step 成功（FINISHED / END）时，ERROR 边不会被选中。

## 3. 基本用法

```java
Flow flow = FlowBuilder.create()
        .start()
        .step(parseStep)
            .onFailure(logErrorStep)   // parseStep 失败 → logErrorStep
        .step(embeddingStep)
            .onFailure(alertStep)      // embeddingStep 失败 → alertStep
        .step(storageStep)
        .end()
        .build();
```

上图存在两条路径：

- 正常路径：`parseStep → embeddingStep → storageStep → END`
- 错误路径：失败的 Step → 对应错误处理节点，随后按该节点自己的出边继续流转

`onFailure(...)` 不改变链式位置，因此 `.step(parseStep).onFailure(logErrorStep)
.step(embeddingStep)` 中 `embeddingStep` 仍接在 `parseStep` 的正常路径之后。

## 4. 失败即结束

```java
Flow flow = FlowBuilder.create()
        .start()
        .step(embeddingStep).onFailureToEnd()
        .step(storageStep)
        .build();
```

`embeddingStep` 失败后直接进入 END，`storageStep` 不会执行，流程**正常结束**
（不抛异常、不触发 `onFlowFailure`）。

> 注意：`onFailureToEnd()` 会提前创建 END 节点，与显式 `to(Flow.END_STEP)`
> 行为一致，其后不能再调用 `.end()`（会抛 "End node already added"），直接
> `build()` 即可。

## 5. 在错误处理节点之后继续编排

错误处理节点执行完后沿它自己的出边流转。需要在错误处理节点后继续接节点时，
用 `from(...)` 显式切换：

```java
FlowBuilder.create()
        .start()
        .step(riskyStep).onFailure(compensateStep)
        .step(normalNextStep)
        .from(compensateStep)
        .step(recoverStep)
        .end()
        .build();
```

## 6. 循环内失败：继续下一次迭代

循环体内的 Step 失败时，如果希望**跳过本次迭代剩余步骤、直接进入下一次迭代判定**，
使用 `onFailureToLoopStart()`。它与 `onFailure` 的区别是：引擎会在 `endLoop()`
时自动把失败路径接回循环判定节点，无需手工编排回边。

### 6.1 无错误处理节点

```java
FlowBuilder.create()
        .start()
        .loop(ctx -> done(ctx) ? "EXIT" : "LOOP", LoopConfig.max(10))
        .step(riskyStep).onFailureToLoopStart()
        .step(nextStep)
        .endLoop()
        .end()
        .build();
```

`riskyStep` 失败（重试耗尽后）时直接回到循环判定节点，`nextStep` 在本次迭代中
被跳过；判定节点返回 `LOOP` 则开始下一次迭代，返回 `EXIT` 则离开循环。

```
          ┌──────── LOOP ────────┐
          ▼                      │
   loop-decider ──LOOP──▶ riskyStep ──正常──▶ nextStep ──▶ loop-decider
          ▲                    │
          └────── ERROR ───────┘
```

### 6.2 带错误处理节点

```java
FlowBuilder.create()
        .start()
        .loop(ctx -> done(ctx) ? "EXIT" : "LOOP", LoopConfig.max(10))
        .step(riskyStep).onFailureToLoopStart(handlerStep)
        .step(nextStep)
        .endLoop()
        .end()
        .build();
```

失败路径为：`riskyStep --ERROR--> handlerStep --正常--> loop-decider`。错误处理
节点执行完成后自动回到循环判定，`nextStep` 同样被跳过。

```
   riskyStep ──ERROR──▶ handlerStep ──▶ loop-decider
```

### 6.3 注意事项

- **失败信息仍可读取**：路由前同样会把 `StepErrorInfo` 写入 `FlowContext`，
  带 handler 时可在 handler 中读取（见第 7 节）
- **重试优先**：`retryTimes` 次重试全部失败后才触发回跳
- **handler 已自动接入判定节点**：不要再为 handler 追加正常出边，否则它会出现
  多条 wildcard 出边，后继节点选择不确定
- **循环上限**：`LoopConfig.max(n)`（默认 20）按 `LOOP` 回边计数。若每轮都失败
  并回跳，最终会抛 `FlowException("Loop exceeded max iterations ...")`
- **想终止整个流程而非继续**：改用 `onFailureToEnd()`（流程正常结束）或
  `onFailure(errorStep)`（路由到错误节点；该节点若无出边，流程随即结束）

## 7. 错误处理 Step 如何读取失败信息

路由前，引擎会把 `StepErrorInfo` 写入 `FlowContext` 的固定 key
（`StepErrorInfo.CONTEXT_KEY = "flow.step.error"`）。错误处理 Step 通过自定义
`InputResolver` 读取：

```java
public class LogErrorStep extends AbstractStep<StepErrorInfo, Void> {

    public LogErrorStep() {
        // 按固定 key 从 context 解析失败信息
        super(context -> context.get(StepErrorInfo.CONTEXT_KEY, StepErrorInfo.class));
    }

    @Override
    public String getName() {
        return "log-error";
    }

    @Override
    public StepType getType() {
        return StepType.STORAGE;
    }

    @Override
    public Class<StepErrorInfo> getInputType() {
        return StepErrorInfo.class;
    }

    @Override
    protected Void doProcess(StepErrorInfo input, FlowContext context) {
        // input.getStepName()    失败的 Step 名称
        // input.getErrorMessage() 异常消息
        // input.getError()       原始异常
        // input.getTimestamp()   失败时间
        return null;
    }

    @Override
    protected void writeOutput(Void output, FlowContext context) {
        // 无产出
    }
}
```

## 8. 并行分支中的错误路由

并行分支内的 Step 同样可以挂错误出口，错误处理节点必须位于**同一并行区域内**
（由现有并行互斥校验保证，跨出区域会被 FlowValidator 拒绝）：

```java
FlowBuilder.create()
        .start()
        .parallel()
        .on()
        .step(branchStepA)
        .on()
        .step(branchStepB).onFailure(branchErrorHandler)
        .endParallel()
        .end()
        .build();
```

分支内 Step 失败并被错误处理节点接管后，该分支继续执行，不会导致整个并行组失败。

## 9. 行为说明

- **未配置错误路由时行为不变**：Step 失败仍包装为 `FlowException` 终止流程
- **错误处理节点自身失败**：可继续为其挂 `onFailure` 形成多级兜底；若它自身
  失败且没有错误出口，则流程终止
- **重试优先于路由**：retryTimes 内的重试全部失败后才会路由
- **循环内回跳不算流程失败**：`onFailureToLoopStart()` 接管后流程继续执行，
  不回调 `onFlowFailure`；但该轮 Step 的 `onStepFailure` 仍会触发
- **监听器**：路由前仍会回调 `onStepFailure`（Step 确实失败了，持久化/监控如实
  记录）；因流程未终止，不回调 `onFlowFailure`
