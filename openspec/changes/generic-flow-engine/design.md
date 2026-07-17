# 设计文档：通用 Flow 引擎

## 1. 设计目标

重新开发通用 Flow 和 FlowBuilder，基于 JGraphT 图数据结构，支持：
- 顺序执行
- 条件分支
- 循环
- 并行执行
- 执行持久化（通过监听器扩展）

## 2. 架构设计

### 2.1 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                        Flow                               │
│  ┌──────────────────────────────────────────────────┐    │
│  │              DirectedGraph                        │    │
│  │  Nodes: FlowNode (Step / Decider)               │    │
│  │  Edges: FlowEdge (status condition)             │    │
│  └──────────────────────────────────────────────────┘    │
│                                                            │
│  Execution Engine: 顺序 / 条件 / 循环 / 并行              │
│  Listeners: Logging / Persistence / Metrics               │
└──────────────────────────────────────────────────────────┘
```

### 2.2 核心类图

```
Flow
  ├── DirectedGraph<FlowNode, FlowEdge>
  ├── startNode: FlowNode
  └── endNode: FlowNode

FlowNode
  ├── step: Step<?, ?> (or null)
  ├── decider: FlowDecider (or null)
  └── name: String

FlowEdge
  └── status: String

FlowBuilder
  ├── graph: DirectedGraph<FlowNode, FlowEdge>
  ├── currentNode: FlowNode
  ├── loopState (for loop tracking)
  └── parallelState (for parallel tracking)

FlowExecutionListener (interface)
  ├── onFlowStart
  ├── onStepStart
  ├── onStepSuccess
  ├── onStepFailure
  ├── onFlowComplete
  └── onFlowFailure
```

## 3. 图结构设计

### 3.1 图类型

使用 JGraphT 的 `DirectedPseudograph`，允许：
- 有向边
- 同一对节点之间可以有多条边（不同条件）
- 自环（用于循环）

### 3.2 节点类型

| 类型 | 表示 | 说明 |
|------|------|------|
| Start | FlowNode(StartStep) | 流程起点 |
| End | FlowNode(EndStep) | 流程终点 |
| Step | FlowNode(Step) | 处理步骤 |
| Decider | FlowNode(FlowDecider) | 决策节点 |
| ParallelStart | FlowNode(ParallelStartStep) | 并行开始 |
| ParallelEnd | FlowNode(ParallelEndStep) | 并行结束 |

### 3.3 边的类型

| 类型 | status | 说明 |
|------|--------|------|
| 无条件 | `*` | 默认顺序流转 |
| 条件 | 自定义字符串 | decider 返回值匹配 |

## 4. 执行引擎设计

### 4.1 执行流程

```
1. 从 startNode 开始
2. 执行当前节点
3. 获取节点状态 (StepStatus / decider result)
4. 查找下一个节点 (匹配边的 status)
5. 移动到下一个节点
6. 重复 2-5 直到 endNode 或 FAILED
```

### 4.2 并行执行

```
ParallelStartStep:
  1. 获取所有并行分支
  2. 为每个分支创建子 FlowContext
  3. 使用 CompletableFuture 并行执行
  4. 返回 FINISHED（立即进入 ParallelEndStep 等待）

ParallelEndStep:
  1. 等待所有分支完成 (FAIL_FAST 策略)
  2. 收集所有子 FlowContext 的结果
  3. 合并子 FlowContext 到主 context
  4. 返回 FINISHED
```

### 4.3 循环执行

```
循环入口: Decider 节点
  - 返回 "CONTINUE" → 继续循环
  - 返回 "EXIT" → 退出循环
  - 达到 maxIterations → 抛出异常
```

## 5. 验证逻辑

### 5.1 验证时机

`FlowBuilder.build()` 时执行所有验证。

### 5.2 验证项

1. **语法校验**：loop/parallel 闭合检查
2. **可达性**：所有节点从 START 可达
3. **条件冲突**：同一节点无重复条件边
4. **并行互斥**：Step 不在多个并行分支
5. **嵌套检测**：不允许嵌套并行
6. **循环检测**：非 loop 构造的环报错

## 6. 持久化设计

### 6.1 监听器模式

```
Flow.execute()
  → listeners.onFlowStart()
  → for each step:
      → listeners.onStepStart()
      → step.process()
      → listeners.onStepSuccess() / onStepFailure()
  → listeners.onFlowComplete() / onFlowFailure()
```

### 6.2 持久化监听器

`PersistentExecutionListener` 实现 `FlowExecutionListener`，
将执行状态通过 `FlowExecutionRepository` 持久化。

## 7. 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| JGraphT Core | 1.5.2 | 图数据结构 |
| Java 21 | - | 语言版本 |
| ConcurrentHashMap | - | FlowContext 存储 |
| CompletableFuture | - | 并行执行 |
| ThreadPoolExecutor | - | 线程池管理 |
