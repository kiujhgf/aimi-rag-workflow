# 开发计划：StructuredChatGenerationStep

## 1. 核心实现

- [x] 1.1 抽取包内 `ChatMessageAssembler`（消息组装、占位符替换、默认 inputResolver）
- [x] 1.2 `ChatGenerationStep` 从 `ChatModel.call()` 切换到 `ChatClient`（`prompt().messages(...).call().content()`）
- [x] 1.3 新增 `StructuredChatGenerationStep<T>`（`extends AbstractStep<List<Message>, T>`）
- [x] 1.4 `StructuredChatGenerationStep` 内部始终走 `stream().content()`，手动追加 `converter.getFormat()` 到最后一条 `UserMessage`，阻塞结束后统一 `convert()`
- [x] 1.5 `ChatGenerationStep` 新增 `streaming(boolean)`：开启时把冷 `Flux<String>` 写入 context key `stream`，跳过默认 outputResolver，不发起请求
- [x] 1.6 converter 转换失败与全空输入抛 `StepExecuteException`
- [x] 1.7 两族 builder：`builder(ChatClient, Class/PTR/converter)` 与 `builder(Class/PTR)` 配置式（内部 `ChatClient.create`）
- [x] 1.8 删除 `metadataKey` 字段、builder 方法及相关元数据写入逻辑
- [x] 1.9 `aimi-rag-generation` 显式声明 `spring-ai-client-chat` 依赖

## 2. 测试

- [x] 2.1 DTO 转换成功并写入 `structured`，格式指令由步骤追加到用户消息
- [x] 2.2 prompt 组装 + 占位符 + 格式指令
- [x] 2.3 `List<Person>` 泛型转换
- [x] 2.4 非法 JSON 抛异常
- [x] 2.5 全空输入抛异常且不调用模型
- [x] 2.6 元数据校验
- [x] 2.7 自定义 `StructuredOutputConverter` 注入与格式指令透传
- [x] 2.8 自定义 `OutputResolver` 不写入默认 `structured` key
- [x] 2.9 两个 Step 测试均改为 `ChatClient.create(mockChatModel)` 注入；流式路径走 `chatModel.stream()`

## 3. 验证

- [x] 3.1 `mvn test -pl aimi-rag-generation`：全部通过

## 4. 收尾

- [x] 4.1 TECH_DEBT W-006 标记完成
- [ ] 4.2 打包安装到本地 Maven 仓库
