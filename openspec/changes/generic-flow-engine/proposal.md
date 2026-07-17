# Proposal: 通用 Flow 引擎重新开发

## 背景

当前 IngestFlowBuilder 存在以下问题：
1. 仅服务于 ingest 流程，不通用
2. 图结构手动实现，不规范
3. 并行和条件分支不能同时使用
4. 缺乏完整的验证机制
5. 执行逻辑分散，难以维护

## 目标

在 `aimi-rag-core` 模块中重新开发通用的 Flow 和 FlowBuilder，具备：
- 基于 JGraphT 的图结构
- 完整的 DSL 语法糖
- 支持顺序、条件、循环、并行组合
- 完整的验证机制
- 可扩展的执行监听器（支持持久化）

## 范围

### 包含
- StepStatus 枚举化
- Step 接口精简（移除 getId、validate）
- AbstractStep 重试逻辑
- StartStep / EndStep
- FlowNode / FlowEdge / Flow
- FlowBuilder DSL（step/decider/loop/parallel）
- 执行引擎（顺序/条件/循环/并行）
- 验证逻辑（语法/可达性/条件冲突/并行互斥/循环检测）
- FlowExecutionListener 接口
- FlowExecutionRepository 接口（持久化骨架）

### 不包含
- 具体的 FlowExecutionRepository 实现（如 JDBC 实现）
- aimi-rag-ingest 模块的迁移
- 现有业务 Step 的修改

## 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| StepStatus | 枚举（FINISHED/FAILED/END） | 类型安全，控制流清晰 |
| 图引擎 | JGraphT | 成熟、功能完善 |
| 并行失败策略 | 默认 FAIL_FAST，可配置 | 快速失败，节省资源 |
| 循环最大迭代 | 默认 20，可配置 | 防止死循环 |
| 线程池 | 用户提供则用户管理，否则内部创建 | 职责清晰 |
| 持久化 | 监听器模式 + Spring 注入 | 核心层纯净，解耦 |

## 影响评估

- **aimi-rag-core**：新增 flow 包，修改 Step 相关类
- **aimi-rag-ingest**：当前不受影响，后续迁移
- **其他模块**：无影响

## 风险

| 风险 | 缓解措施 |
|------|---------|
| JGraphT 学习成本 | 先从简单有向图开始，逐步深入 |
| 并行执行复杂度 | 分阶段实现，先串行后并行 |
| 验证逻辑遗漏 | 参考 Spring Batch 验证规则 |
| 与现有代码不兼容 | 旧的 IngestFlowBuilder 暂时保留，新开发独立 |
