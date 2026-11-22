# 开发计划：循环内失败 Continue（onFailureToLoopStart）

## 概述

为循环体内 Step 提供失败后 continue 语义（经 decider 回边），复用既有 ERROR 路由机制。

**模块**：aimi-rag-core（仅 FlowBuilder + 测试）

---

## 阶段一：DSL 实现

- [x] Task 1.1: FlowBuilder 挂起回边与 DSL 方法
  - **文件**：`aimi-rag-core/src/main/java/.../flow/FlowBuilder.java`
  - 新增 `pendingLoopContinues` 列表（记录 source 与可选 handler）
  - `onFailureToLoopStart()`：仅 loop 内 Step 后可调，登记挂起回边
  - `onFailureToLoopStart(Step handler)`：加 ERROR 边到 handler，登记 handler→decider 挂起边
  - 非 loop 内或 currentNode 非 Step 时抛 FlowValidationException
  - 调用后 currentNode 不变

- [x] Task 1.2: endLoop() 解析挂起回边
  - **文件**：`FlowBuilder.java`
  - deciderNode 创建后：无 handler → `source -ERROR-> decider`；有 handler → `handler -*-> decider`
  - 解析后清空挂起列表
  - loop 未闭合时（validateSyntax 已有）不解析

---

## 阶段二：单元测试

- [x] Task 2.1: 循环失败 continue 测试
  - **文件**：`aimi-rag-core/src/test/java/.../flow/LoopErrorContinueTest.java`（新建）
  - 失败后直接 continue：decider 收到下一轮迭代，失败后剩余循环体步骤不执行
  - 失败 → handler → continue：handler 执行 1 次，StepErrorInfo 可读，随后进入下一轮
  - 每轮都失败时由 maxIterations 兜底抛 FlowException（防死循环）
  - 循环外调用 onFailureToLoopStart 抛 FlowValidationException
  - 既有测试全部回归通过
