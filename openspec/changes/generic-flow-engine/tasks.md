# 开发计划：通用 Flow 引擎

## 概述

重新开发通用 Flow 和 FlowBuilder，基于 JGraphT 图数据结构，支持条件分支、循环、并行等组合流程。

**模块**：aimi-rag-core
**预计总工时**：12-15 天

---

## 阶段一：核心模型变更（2 天）

### Task 1.1: StepStatus 枚举化

- **文件**：`aimi-rag-core/src/main/java/.../model/StepStatus.java`
- **内容**：
  - 将 StepStatus 从类改为枚举
  - 保留 FINISHED、FAILED、END 三个值
  - 移除自定义构造函数和 matches 方法
  - 保留 equals/hashCode
- **验证**：编译通过，StepStatus.values() 包含 3 个值

### Task 1.2: Step 接口调整

- **文件**：`aimi-rag-core/src/main/java/.../step/Step.java`
- **内容**：
  - 移除 getId() 方法
  - 移除 validate()、validatePredecessors()、validatePreviousStep() 方法
  - 添加 getRetryTimes() 方法
  - 添加 setRetryTimes(int retryTimes) 方法
  - 添加 getRetryableExceptions() 方法
- **验证**：编译通过（注意其他模块可能有编译错误，先忽略）

### Task 1.3: AbstractStep 调整

- **文件**：`aimi-rag-core/src/main/java/.../step/AbstractStep.java`
- **内容**：
  - 移除 id 字段和 UUID 生成
  - 移除 getId() 方法
  - 添加 retryTimes 字段（默认 1）
  - 添加 retryableExceptions 字段（默认 Exception.class）
  - 实现重试逻辑在 process() 中
  - 实现 isRetryable() 私有方法
  - status 默认值从 COMPLETED 改为 FINISHED
- **验证**：编译通过，重试逻辑单元测试

### Task 1.4: StartStep 和 EndStep

- **文件**：
  - `aimi-rag-core/src/main/java/.../step/StartStep.java`（新建）
  - `aimi-rag-core/src/main/java/.../step/EndStep.java`（新建）
- **内容**：
  - 继承 AbstractStep<Void, Void>
  - 单例模式（INSTANCE）
  - getName() 返回 "START"/"END"
  - getType() 返回 StepType.START / StepType.END
  - doProcess() 返回 null
- **验证**：实例化成功，process() 返回 FINISHED

### Task 1.5: StepType 枚举扩展

- **文件**：`aimi-rag-core/src/main/java/.../model/StepType.java`
- **内容**：
  - 添加 START 和 END 枚举值
- **验证**：编译通过

---

## 阶段二：图结构设计（2 天）

### Task 2.1: 添加 JGraphT 依赖

- **文件**：`pom.xml`（父 pom）和 `aimi-rag-core/pom.xml`
- **内容**：
  - 在 dependencyManagement 中添加 JGraphT 版本管理
  - 在 aimi-rag-core 中添加 jgrapht-core 依赖
- **版本**：1.5.2
- **验证**：mvn dependency:resolve 成功

### Task 2.2: FlowNode 类

- **文件**：`aimi-rag-core/src/main/java/.../flow/FlowNode.java`（新建）
- **内容**：
  - 字段：step、decider、name
  - 两个构造函数：Step 和 FlowDecider
  - equals/hashCode 基于 Step 或 name
  - execute(FlowContext) 方法
  - getStatus() 方法
- **验证**：单元测试验证 equals/hashCode

### Task 2.3: FlowEdge 类

- **文件**：`aimi-rag-core/src/main/java/.../flow/FlowEdge.java`（新建）
- **内容**：
  - 字段：status（String）
  - 默认构造函数：status = "*"
  - 带参构造函数：FlowEdge(String status)
  - getStatus() 方法
- **验证**：单元测试

### Task 2.4: Flow 类（骨架）

- **文件**：`aimi-rag-core/src/main/java/.../flow/Flow.java`（新建）
- **内容**：
  - 静态常量：START_STEP, END_STEP
  - 字段：DirectedGraph<FlowNode, FlowEdge> graph
  - 字段：startNode, endNode
  - 字段：listeners 列表
  - addListener(FlowExecutionListener) 方法
  - execute(FlowContext) 方法（先空实现）
- **验证**：编译通过

---

## 阶段三：FlowBuilder DSL（3 天）

### Task 3.1: FlowBuilder 核心类

- **文件**：`aimi-rag-core/src/main/java/.../flow/FlowBuilder.java`（新建）
- **内容**：
  - 静态 create() 工厂方法
  - 字段：graph（DirectedGraph）
  - 字段：currentNode（当前节点，用于 .step()/.decider() 链式调用）
  - start() / end() 方法
  - build() 方法（先只构建图，不验证）
- **验证**：空流程构建测试

### Task 3.2: .step() 语法糖

- **文件**：`FlowBuilder.java`
- **内容**：
  - step(Step) 方法
  - 检查 Step 是否已存在于图中（基于 equals）
  - 不存在则添加为新 FlowNode
  - 连接当前节点到新节点（无条件边）
  - 更新 currentNode
- **验证**：顺序流程构建测试

### Task 3.3: .decider() 语法糖

- **文件**：`FlowBuilder.java`
- **内容**：
  - decider(FlowDecider, String name) 方法
  - 检查 decider 是否已存在（基于 name）
  - 不存在则添加为新 FlowNode
  - 连接当前节点到新节点
  - 更新 currentNode
- **验证**：decider 节点添加测试

### Task 3.4: 条件分支 .on().to()

- **文件**：`FlowBuilder.java` + TransitionBuilder
- **内容**：
  - on(String status) 返回 TransitionBuilder
  - TransitionBuilder.to(Step) 方法
  - to(Step) 查找或创建目标节点
  - 添加带条件的边
  - .from(Step) 方法：切换当前节点
  - .to(Flow.END_STEP) 支持
- **验证**：条件分支构建测试

### Task 3.5: .loop() / .endLoop() 语法糖

- **文件**：`FlowBuilder.java` + LoopConfig
- **内容**：
  - LoopConfig 类：maxIterations（默认 20）
  - loop(FlowDecider) 方法
  - loop(FlowDecider, LoopConfig) 方法
  - endLoop() 方法
  - 内部状态：inLoop、loopDeciderNode
  - 构建时：循环末尾节点 → decider → 循环开头 / 循环外
- **验证**：循环构建测试

### Task 3.6: .parallel() / .endParallel() 语法糖

- **文件**：`FlowBuilder.java` + ParallelGroup
- **内容**：
  - parallel() 方法
  - parallel(ExecutorService) 方法
  - on() 方法（开始一个并行分支）
  - endParallel() 方法
  - 内部状态：inParallel、parallelBranches
  - ParallelStartStep / ParallelEndStep 包装节点
  - 构建时：ParallelStart → 各分支 → ParallelEnd
- **验证**：并行流程构建测试

---

## 阶段四：执行引擎（3 天）

### Task 4.1: 顺序执行引擎

- **文件**：`Flow.java`
- **内容**：
  - execute() 方法实现
  - 从 startNode 开始
  - 查找下一个节点（无条件边）
  - 执行节点
  - 处理 FAILED / END 状态
  - 直到 endNode
- **验证**：顺序流程执行测试

### Task 4.2: 条件分支执行

- **文件**：`Flow.java`
- **内容**：
  - decider 节点执行逻辑
  - 根据 decider 返回值匹配条件边
  - 支持通配符 "*" 匹配
  - 无匹配条件时的默认处理
- **验证**：条件分支执行测试

### Task 4.3: 循环执行

- **文件**：`Flow.java`
- **内容**：
  - 循环计数逻辑
  - 检测循环条件
  - 超过最大迭代时报错
  - 循环退出处理
- **验证**：循环执行测试

### Task 4.4: 并行执行引擎

- **文件**：`Flow.java`
- **内容**：
  - ParallelStartStep 执行逻辑
  - 每个分支作为子流程执行
  - 使用 CompletableFuture 并行
  - 默认线程池创建（未提供时）
  - FAIL_FAST 失败策略
  - ParallelEndStep 等待所有完成
- **验证**：并行执行测试

### Task 4.5: 上下文合并

- **文件**：`Flow.java` + ContextMergeStrategy
- **内容**：
  - ContextMergeStrategy 接口
  - LastWriteWinMergeStrategy（默认）
  - 子 FlowContext 创建
  - 合并到主 FlowContext
- **验证**：上下文合并测试

---

## 阶段五：验证逻辑（2 天）

### Task 5.1: 语法糖校验

- **文件**：`FlowBuilder.java`
- **内容**：
  - build() 时检查 loop 闭合
  - build() 时检查 parallel 闭合
  - 未闭合抛出 FlowValidationException
- **验证**：未闭合检测测试

### Task 5.2: 孤立节点检测

- **文件**：`FlowBuilder.java`
- **内容**：
  - 从 START 节点开始 BFS/DFS
  - 标记所有可达节点
  - 检查是否有未标记的节点
  - 有孤立节点抛出异常
- **验证**：孤立节点检测测试

### Task 5.3: 条件冲突检测

- **文件**：`FlowBuilder.java`
- **内容**：
  - 遍历所有 decider 节点的出边
  - 检查同一节点是否有相同 status 的边
  - 有冲突抛出异常
- **验证**：条件冲突检测测试

### Task 5.4: 并行任务互斥检测

- **文件**：`FlowBuilder.java`
- **内容**：
  - 检查同一 Step 是否出现在多个并行分支
  - 并行内部节点不能连接到外部
  - 嵌套并行检测
- **验证**：并行互斥检测测试

### Task 5.5: 循环闭环检测

- **文件**：`FlowBuilder.java`
- **内容**：
  - 检测图中的环
  - 使用 DFS 检测
  - 非 loop 构造的环抛出异常
- **验证**：循环检测测试

---

## 阶段六：执行监听器（1 天）

### Task 6.1: FlowExecutionListener 接口

- **文件**：`aimi-rag-core/src/main/java/.../flow/FlowExecutionListener.java`（新建）
- **内容**：
  - onFlowStart
  - onStepStart
  - onStepSuccess
  - onStepFailure
  - onFlowComplete
  - onFlowFailure
  - 所有方法 default 空实现
- **验证**：接口定义

### Task 6.2: 执行引擎集成监听器

- **文件**：`Flow.java`
- **内容**：
  - Flow 类中维护 listeners 列表
  - addListener() 方法
  - execute() 各阶段调用监听器
  - 异常时也调用监听器
- **验证**：监听器调用测试

### Task 6.3: FlowExecutionRepository 接口

- **文件**：`aimi-rag-core/src/main/java/.../flow/FlowExecutionRepository.java`（新建）
- **内容**：
  - createExecution(flowName, inputContext)
  - saveStepExecution(executionId, record)
  - saveStepFailure(executionId, record)
  - updateExecutionStatus(executionId, status)
  - getExecutionHistory(executionId)
  - StepExecutionRecord 类
  - StepFailureRecord 类
- **验证**：接口定义

### Task 6.4: PersistentExecutionListener 骨架

- **文件**：`aimi-rag-core/src/main/java/.../flow/PersistentExecutionListener.java`（新建）
- **内容**：
  - 实现 FlowExecutionListener
  - 持有 FlowExecutionRepository
  - 各阶段调用 repository 方法
  - executionId 保存在 context 中
- **验证**：骨架代码编译通过

---

## 阶段七：单元测试（2 天）

### Task 7.1: 核心模型测试

- StepStatus 枚举测试
- Step 接口变更测试
- AbstractStep 重试逻辑测试
- StartStep/EndStep 测试

### Task 7.2: 图结构测试

- FlowNode equals/hashCode 测试
- FlowEdge 测试
- 图构建测试

### Task 7.3: Builder DSL 测试

- 顺序流程构建测试
- 条件分支构建测试
- 循环构建测试
- 并行构建测试

### Task 7.4: 执行引擎测试

- 顺序执行测试
- 条件分支执行测试
- 循环执行测试
- 并行执行测试
- 上下文合并测试

### Task 7.5: 验证逻辑测试

- 语法校验测试
- 孤立节点检测测试
- 条件冲突检测测试
- 并行互斥检测测试
- 循环检测测试

---

## 依赖关系

```
阶段一 → 阶段二 → 阶段三 → 阶段四 → 阶段五 → 阶段六 → 阶段七
                ↑
                └── 阶段七（各阶段完成后补充测试）
```

---

## 里程碑

| 里程碑 | 完成内容 |
|--------|---------|
| M1 | 核心模型变更完成，编译通过 |
| M2 | 图结构设计完成，Flow 骨架完成 |
| M3 | FlowBuilder DSL 全部语法糖可用 |
| M4 | 执行引擎全部功能可用 |
| M5 | 验证逻辑全部完成 |
| M6 | 执行监听器 + 持久化接口完成 |
| M7 | 单元测试全部通过 |
