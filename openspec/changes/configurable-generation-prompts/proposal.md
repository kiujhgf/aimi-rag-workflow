## 背景

`ChatGenerationStep` 目前只能从 `FlowContext` 的 `messages` 读取完整消息列表，调用方必须自己构造
`SystemMessage` / `UserMessage`。对于「固定人格 + 单轮提问」这类最常见场景，写一个前置 Step 只是为了
拼两条消息，成本不划算。

## 目标

- 支持通过 builder 配置 `systemPrompt` 与 `userPrompt`
- `messages` 非空时完全透传，保持既有行为不变
- 两个 prompt 支持 `{key}` 占位符，运行时从 `FlowContext` 取值

## 非目标

- 不引入 PromptTemplate 文件/模板引擎
- 不恢复该 Step 内部的会话记忆管理（仍由外部 Step 负责，见 TECH_DEBT W-002/W-004）

## 影响

- **aimi-rag-generation**：`ChatGenerationStep` 新增两个配置项与消息组装逻辑
- 默认 inputResolver 由 `ByKeyResolver` 改为 `ByKeyOrDefaultResolver`（`messages` 缺失时视为空列表）
- 其余模块无变更
