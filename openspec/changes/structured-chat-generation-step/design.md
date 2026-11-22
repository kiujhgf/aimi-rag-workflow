# 设计：StructuredChatGenerationStep

## 1. 模块结构

```
aimi-rag-generation/src/main/java/io/github/aimi/rag/workflow/generation/step/
├── ChatGenerationStep.java              # 既有，改用 ChatClient；新增 streaming 选项
├── ChatMessageAssembler.java            # 包内共享消息组装
└── StructuredChatGenerationStep.java    # 泛型结构化输出，内部始终流式
```

## 2. 类设计

`GenerationStep extends AbstractStep<List<Message>, String>` 输出类型固定为 `String`，
因此 `StructuredChatGenerationStep<T>` 直接继承 `AbstractStep<List<Message>, T>`：

| 成员 | 值 |
|------|----|
| `getName()` | `structured-chat-generation` |
| `getType()` | `StepType.GENERATION` |
| `getCategory()` | `FlowCategory.QUERY` |
| `getInputType()` | `List<Message>` |
| 默认输入 | `messages`（缺失视为空列表，`ByKeyOrDefaultResolver`） |
| 默认输出 | `structured`，可自定义 `OutputResolver<T>` |

依赖：`ChatClient`、`StructuredOutputConverter<T>`、`systemPrompt`、`userPrompt`（后两者可空）。

## 3. 执行流程

### 3.1 ChatGenerationStep（非流式）

```
input(messages) ──► ChatMessageAssembler.resolve(...)
                        │ messages 非空 → 原样返回
                        │ 为空 → systemPrompt + userPrompt 组装（{key} 占位符替换）
                        │ 全空 → StepExecuteException
                        ▼
        chatClient.prompt().messages(messages).call().content()
                        ▼
              OutputResolver 写入 FlowContext（默认 key answer）
```

### 3.2 ChatGenerationStep（流式）

```
builder.streaming(true)
                        ▼
input(messages) ──► ChatMessageAssembler.resolve(...)
                        ▼
  Flux<String> flux = chatClient.prompt().messages(messages).stream().content()
  context.set("stream", flux)          // 冷流，不订阅，不触发请求
  return null                          // 跳过默认 outputResolver
```

下游节点从 `context.get("stream")` 取回冷流自行订阅，订阅时才真正发起模型请求。

### 3.3 StructuredChatGenerationStep<T>（始终流式）

```
input(messages) ──► ChatMessageAssembler.resolve(...)
                        ▼
      appendFormatInstructions(messages, converter.getFormat())
                        │ 追加到最后一条 UserMessage 末尾（不存在则新增）
                        ▼
        chatClient.prompt().messages(streamingMessages).stream().content()
                        │ 阻塞等待全部分片 → StringBuilder
                        ▼
          converter.convert(builder.toString())
                        ▼
              OutputResolver 写入 FlowContext（默认 key structured）
```

`appendFormatInstructions` 行为与 ChatClient 非流式路径一致：
找到最后一条 `UserMessage`，用 `System.lineSeparator() + format` 拼接后替换。

## 4. Builder 设计

与 `ChatGenerationStep` 对齐，提供两族入口：

- `builder(ChatClient, Class<T>)` / `builder(ChatClient, ParameterizedTypeReference<T>)` / `builder(ChatClient, StructuredOutputConverter<T>)`
- `builder(Class<T>)` / `builder(ParameterizedTypeReference<T>)` → `ConfigurableBuilder`（`apiKey` / `baseUrl` / `model`，默认 `gpt-4o-mini`，内部以 `ChatClient.create(chatModel)` 构建）

两族 builder 均支持 `systemPrompt` / `userPrompt` / `inputResolver` / `outputResolver`。

`ChatGenerationStep` 额外支持 `streaming(boolean)`，`StructuredChatGenerationStep` 不暴露该选项（内部固定流式）。

## 5. ChatMessageAssembler

包内私有工具类，承载原 `ChatGenerationStep` 的私有逻辑：

- `resolve(input, systemPrompt, userPrompt, context, stepName)`：透传或组装，全空抛异常
- `applyPlaceholders(template, context)`：`{标识符}` 替换，未命中保留原样（保护 JSON 片段）
- `defaultInputResolver()`：`ByKeyOrDefaultResolver("messages", List.of())`

`ChatGenerationStep` 与 `StructuredChatGenerationStep` 均委托该类。

## 6. 错误处理

| 场景 | 行为 |
|------|------|
| messages 为空且未配置任何 prompt | `StepExecuteException`，不调用模型 |
| 模型输出无法转换为目标类型 | `StepExecuteException`（包装运行时异常后交由 `AbstractStep` 处理） |
| 流式请求过程中抛异常 | 由 `doProcess` 的 `catch (RuntimeException)` 包装成 `StepExecuteException` |
| 模型调用异常 | 由 `AbstractStep` 既有重试/失败路由机制处理 |

## 7. 测试策略

Mock `ChatModel`，用 `ChatClient.create(mockChatModel)` 注入；
`ChatGenerationStep.call()` 路径下用 `chatModel.call()` 返回假 `ChatResponse`，
流式路径下用 `chatModel.stream()` 返回 `Flux.just(chunk1, chunk2, ...)`：

1. DTO 转换成功并写入 `structured`，格式指令由步骤追加到用户消息
2. prompt 组装 + 占位符（2 条消息，格式指令追加在用户消息尾部）
3. `ParameterizedTypeReference<List<Person>>` 泛型转换
4. 非法 JSON 抛 `StepExecuteException`
5. 无消息且无 prompt 抛异常且不调用模型
6. 元数据（name / type / category）
7. 自定义 `StructuredOutputConverter` 注入与格式指令透传
8. 自定义 `OutputResolver` 不写入默认 `structured` key
9. `ChatGenerationStep.streaming(true)` 时冷 Flux 写入 `stream` key，不触发模型调用，下游订阅后才触发
