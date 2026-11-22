# 设计文档：循环内失败 Continue（onFailureToLoopStart）

## 1. 设计目标

为循环体内的 Step 提供 Java `continue` 语义：失败后（可先经错误处理节点）回到
循环 decider，由 decider 决定是否进入下一轮迭代，且迭代计数与 maxIterations
保护不失效。

## 2. 核心设计

### 2.1 DSL 形态

```java
FlowBuilder.create()
        .start()
        .loop(decider, LoopConfig.of(10))
        .step(riskyStep)
            .onFailureToLoopStart(errorHandler)  // 失败 → errorHandler → 回 decider（continue）
        .step(otherStep)
            .onFailureToLoopStart()              // 失败 → 直接回 decider
        .endLoop()
        .step(afterLoop)
        .end()
        .build();
```

目标图结构：

```
        ┌──────────────── LOOP ────────────────┐
        ▼                                      │
  riskyStep ──> otherStep ──> [loop decider] ──┴──> afterLoop
     │   \                          ▲
     │    └──ERROR──> errorHandler ─┘ (wildcard)
     └──────────────ERROR───────────▶ (otherStep 直达 decider)
```

- `onFailureToLoopStart()`：`currentStep -ERROR-> deciderNode`
- `onFailureToLoopStart(handler)`：`currentStep -ERROR-> handler` 且 `handler -*-> deciderNode`

### 2.2 为什么回边指向 decider 而非循环首节点

Flow 主循环中迭代计数器挂在 decider 节点访问上（每次 decider 执行时 +1，超过
maxIterations 抛 FlowException）。若回边直达首节点，失败重试型死循环不会被
计数器拦截。指向 decider 后：

1. 计数正常累加，maxIterations（默认 20）兜底
2. decider 可以基于 context（如 StepErrorInfo）决定本轮失败后是 continue 还是退出循环

### 2.3 挂起回边机制（FlowBuilder）

调用 DSL 时 decider 节点尚不存在（endLoop 才创建），因此采用挂起解析：

```
FlowBuilder 新增字段：
  pendingLoopContinues: List<PendingContinue>   // PendingContinue = { FlowNode source, FlowNode handlerOrNull }

onFailureToLoopStart()：
  ensureInLoop() + ensureCurrentIsStep()
  pendingLoopContinues.add({ currentNode, null })

onFailureToLoopStart(handler)：
  ensureInLoop() + ensureCurrentIsStep()
  graph.addEdge(currentNode, handlerNode, FlowEdge(ERROR))
  pendingLoopContinues.add({ currentNode, handlerNode })   // handler→decider 的 wildcard 边挂起

endLoop() 末尾（deciderNode 创建后）：
  for each pending：
    target = handlerOrNull != null ? handler : source
    若 handler != null：graph.addEdge(handler, deciderNode, wildcard)
    否则：graph.addEdge(source, deciderNode, FlowEdge(ERROR))
  pendingLoopContinues.clear()
```

约束：

- 两个方法仅允许 `inLoop == true` 且 currentNode 为 Step 节点时调用，否则抛
  FlowValidationException
- 调用后 currentNode 不变（与 onFailure 一致的链式语义）
- handler 的 wildcard 出边由本 DSL 独占：用户不应再给 handler 接其他正常出边

### 2.4 循环检测兼容性

校验器仅移除 LOOP 边后做 CycleDetector：

- 移除 `decider -LOOP-> 首节点` 后，`handler -*-> decider` 与
  `step -ERROR-> handler` 均无法再回到上游，不成环，校验天然通过
- 因此 **FlowValidator 零改动**

### 2.5 运行时行为

复用 flow-error-routing 已有机制，Flow 主循环零改动：

1. riskyStep 重试耗尽抛 StepExecuteException（或返回 FAILED）
2. findErrorNode 找到 ERROR 边 → 写 StepErrorInfo → notifyStepFailure → 路由
3. 有 handler：执行 handler 后走其 wildcard 边到 decider；无 handler：直达 decider
4. decider 累加迭代计数并裁决：返回 "LOOP" → 下一轮；否则退出循环

## 3. 改动清单

| 文件 | 改动 |
|------|------|
| `core/flow/FlowBuilder.java` | 新增 `onFailureToLoopStart()` / `onFailureToLoopStart(Step)`、`ensureInLoop()`、挂起回边列表、`endLoop()` 内解析 |
| `core/flow/Flow.java` | 无改动 |
| `core/flow/FlowValidator.java` | 无改动 |
| 测试 `FlowErrorRoutingTest` 或新测试类 | 新增循环 continue 用例 |
