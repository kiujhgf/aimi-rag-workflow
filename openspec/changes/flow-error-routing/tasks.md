# 开发计划：Flow 级别错误路由

## 概述

为 Flow 引擎的 Step 提供失败后的自定义路由能力（onFailure / onFailureToEnd）。

**模块**：aimi-rag-core

---

## 阶段一：核心模型

- [x] Task 1.1: FlowEdge 错误边
  - **文件**：`aimi-rag-core/src/main/java/.../flow/FlowEdge.java`
  - 新增 `public static final String ERROR = "ERROR"`
  - 新增 `isError()` 方法

- [x] Task 1.2: StepErrorInfo 模型
  - **文件**：`aimi-rag-core/src/main/java/.../model/StepErrorInfo.java`（新建）
  - `CONTEXT_KEY = "flow.step.error"`
  - 字段：stepName / errorMessage / error / timestamp
  - 构造函数 `StepErrorInfo(String stepName, Throwable error)`

---

## 阶段二：DSL

- [x] Task 2.1: FlowBuilder.onFailure / onFailureToEnd
  - **文件**：`FlowBuilder.java`
  - `onFailure(Step<?, ?> errorStep)`：当前节点 → errorStep 加 ERROR 边
  - `onFailureToEnd()`：当前节点 → END 加 ERROR 边
  - 两个方法均要求 currentNode 为 Step 节点，否则抛 FlowValidationException
  - 调用后 currentNode 保持不变

---

## 阶段三：执行引擎

- [x] Task 3.1: Flow 主循环错误路由
  - **文件**：`Flow.java`
  - 新增 `findErrorNode(FlowNode)` 私有方法
  - `catch (StepExecuteException)` 置于 `catch (Exception)` 之前：有 ERROR 边则
    写 StepErrorInfo、notifyStepFailure、路由继续；无则抛 FlowException
  - `status == FAILED` 分支同样处理

- [x] Task 3.2: 并行分支错误路由
  - **文件**：`Flow.java`
  - `executeBranch()` 内对 StepExecuteException / FAILED 做同样路由处理

---

## 阶段四：单元测试

- [x] Task 4.1: 错误路由测试
  - **文件**：`aimi-rag-core/src/test/java/.../flow/FlowErrorRoutingTest.java`（新建）
  - Step 失败 → 路由到错误处理 Step，流程正常完成，错误 Step 执行 1 次
  - StepErrorInfo 写入 context（stepName / errorMessage 正确）
  - 未配置 onFailure 时行为不变（抛 FlowException）
  - onFailureToEnd：失败后直接到 END，后续正常路径不执行
  - Step 成功时错误路径不执行
  - 错误处理 Step 自身失败且无路由 → 抛 FlowException
  - onFailure 后链式 .step() 仍接在正常路径上
  - 并行分支内 Step 失败路由
  - 既有测试全部回归通过
