# 开发计划：ChatGenerationStep 可配置 prompt

## 1. 核心实现

- [x] 1.1 `ChatGenerationStep` 增加 `systemPrompt` / `userPrompt` 字段与构造参数
- [x] 1.2 `resolveMessages`：`messages` 非空直接透传；否则按 system → user 顺序组装
- [x] 1.3 `applyPlaceholders`：`{标识符}` 从 `FlowContext` 取值，未命中保留原样
- [x] 1.4 三者皆空抛 `StepExecuteException`
- [x] 1.5 默认 inputResolver 改为 `ByKeyOrDefaultResolver("messages", List.of())`
- [x] 1.6 `Builder` 与 `ConfigurableBuilder` 均暴露 `systemPrompt(...)` / `userPrompt(...)`

## 2. 测试

- [x] 2.1 system + user 同时配置
- [x] 2.2 仅 userPrompt
- [x] 2.3 messages 非空时 prompt 不生效
- [x] 2.4 占位符替换
- [x] 2.5 未命中占位符与 JSON 花括号保持原样
- [x] 2.6 三者皆空抛异常且不调用模型
