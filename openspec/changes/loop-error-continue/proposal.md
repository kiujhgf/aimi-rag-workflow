# Proposal: 循环内失败 Continue（onFailureToLoopStart）

## 背景

flow-error-routing 变更已为 Step 提供 Flow 级错误路由（onFailure / onFailureToEnd）。
但循环（loop/endLoop）场景下仍缺少 Java `continue` 语义：循环体内某个 Step 失败后，
无法"处理完本轮失败，直接进入下一轮迭代"：

- `onFailure(handler)` 指回循环首节点会构成非 LOOP 边回环，被 FlowValidator 拒绝
- loop decider 节点在 `endLoop()` 时才创建，DSL 层拿不到，无法把错误边指过去
- 迭代计数器挂在 decider 节点上，绕过 decider 的回边会导致 maxIterations 失效（死循环风险）

## 目标

为循环体内的 Step 提供"失败后 continue"的 DSL：

- `onFailureToLoopStart()`：失败（重试耗尽 / FAILED）后直接回到循环 decider，由 decider 决定是否进入下一轮
- `onFailureToLoopStart(handler)`：先路由到错误处理节点，执行完再回循环 decider
- 回边必须经过 decider 节点，保证迭代计数与 maxIterations 保护生效

## 范围

### 包含
- `FlowBuilder` 新增 `onFailureToLoopStart()` / `onFailureToLoopStart(Step)` DSL
- 挂起回边在 `endLoop()` 时解析到 decider 节点的机制
- 单元测试（含 maxIterations 防死循环验证）

### 不包含
- 循环外 Step 的 continue 语义（无意义，普通错误路由已覆盖）
- break 语义增强（现有 `onFailure(循环外节点)` 已支持）
- decider 节点自身的失败路由

## 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 回边目标 | 循环 decider 节点（而非循环首节点） | 迭代计数器挂在 decider 访问上，绕过会导致 maxIterations 失效；且由 decider 统一裁决是否继续 |
| handler 到 decider 的边 | 普通 wildcard 边 | decider→首节点是 LOOP 边，校验器移除 LOOP 边后该路径不成环，天然过循环检测 |
| 无 handler 版本 | ERROR 边直接指向 decider | 失败即 continue，无需中间节点 |
| DSL 调用时机 | 仅允许在 loop()/endLoop() 之间、跟在 step 后 | 否则抛 FlowValidationException |
| 回边解析时机 | endLoop() 时统一解析挂起请求 | 调用 DSL 时 decider 节点尚不存在 |
| 复用 StepErrorInfo | 路由前照常写入 context | 错误处理节点可读取失败详情，与 flow-error-routing 一致 |

## 影响评估

- **aimi-rag-core**：仅 `FlowBuilder`（新增挂起回边列表 + 两个 DSL 方法 + endLoop 解析）
- **Flow / FlowValidator**：零改动（回边是普通边 + 既有 ERROR 路由机制，校验天然通过）

## 风险

| 风险 | 缓解措施 |
|------|---------|
| 每轮都失败导致无限循环 | 回边必经 decider，迭代计数 + maxIterations 兜底（默认 20） |
| handler 被多处复用导致 wildcard 边劫持正常路径 | 文档约束 handler 应专用；同一节点挂多条 wildcard 边本就是未定义行为 |
| 嵌套循环中回边归属错误 | 现有 DSL 已禁止嵌套循环，不存在该场景 |
