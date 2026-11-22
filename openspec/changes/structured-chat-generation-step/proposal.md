## 背景

`ChatGenerationStep` 的 `doProcess` 固定返回 `String` 文本，无法让 LLM 按指定结构（DTO / JSON schema）
产出结构化结果，调用方只能自行解析 JSON，既脆弱又重复。对应技术债 **TECH_DEBT W-006**。

模型流式输出是下游节点（如 SSE 推送）的常用需求，因此同时补充两项能力：
`ChatGenerationStep` 可选择把冷 `Flux<String>` 放入 context 交给下游订阅；
`StructuredChatGenerationStep` 内部始终用流式接口拉取全量分片再转换。

## 目标

- 两个生成 Step 统一从 `ChatModel.call()` 切换到 `ChatClient`（`prompt().messages(...)`）
- 新增支持结构化输出的生成 Step，基于流式 API 拉全量分片，再由 `StructuredOutputConverter` 转换
- 支持 `Class<T>`（DTO）与 `ParameterizedTypeReference<T>`（如 `List<Person>`）两种输出类型
- 消息组装规则与 `ChatGenerationStep` 完全一致（messages 透传 / systemPrompt + userPrompt 组装，含 `{key}` 占位符）
- `ChatGenerationStep` 增加 `streaming(boolean)` 选项：开启时把冷 `Flux<String>` 放入 context key `stream`

## 非目标

- 不做完整响应元数据（`ChatResponse` / token 用量）输出
- 不做 provider 原生的 `responseFormat` 参数适配（converter 的格式指令已覆盖通用场景）
- 下游节点如何消费 `stream` key 不属于本 change 范围
- `StructuredChatGenerationStep` 不直接暴露 Flux；结构化必须等完整 JSON 才能转换

## 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 调用范式 | 统一改用 `ChatClient` | 与 Spring AI 推荐用法一致 |
| 类设计 | 新建 `StructuredChatGenerationStep<T>`，直接 `extends AbstractStep<List<Message>, T>` | `GenerationStep` 输出类型固定为 `String`，继承无法改变；独立类保证类型安全 |
| 结构化输出 | 始终走 `stream().content()` + `converter.convert(joined)` | `StreamResponseSpec` 没有 `entity()` 重载，手动把 `converter.getFormat()` 追加到最后一条 `UserMessage`（与 ChatClient 非流式行为一致） |
| 文本流式 | `ChatGenerationStep.builder().streaming(true)`，把冷 `Flux<String>` 写入 `stream` key | 让下游节点订阅时才触发请求；避免 Step 侧重复消费 |
| 默认输出 key | `structured` / `answer` / `stream` | 与 String 类型的 `answer` 区分，避免下游误当文本用 |
| 消息组装复用 | 抽取包内 `ChatMessageAssembler`，两个 Step 共用 | 消除重复，保持两条路径行为一致 |
| converter | 默认 `BeanOutputConverter`，builder 允许传入任意 `StructuredOutputConverter<T>` | 兼容 `MapOutputConverter` 等其它转换器 |

## 影响

- **aimi-rag-generation**：新增 `StructuredChatGenerationStep` 与包内 `ChatMessageAssembler`；`ChatGenerationStep` 改用 `ChatClient` 并委托 assembler；`ChatGenerationStep` 新增 `streaming` 选项；`StructuredChatGenerationStep` 内部改用流式接口 + 手动追加格式指令
- 其余模块无变更
