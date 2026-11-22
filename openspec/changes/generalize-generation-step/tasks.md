# 开发计划：Generation Step 通用化

## 概述

将 `GenerationStep` 契约改为 `List<Message>` → `String`，用 provider 无关的
`ChatGenerationStep` 替代 `OpenAiGenerationStep`；默认从 context 的 `messages`
读取消息、把答案写入 `answer`，prompt 组装与结果包装交由调用方。

**模块**：aimi-rag-query、aimi-rag-generation

---

## 1. 依赖调整

- [x] 1.1 `aimi-rag-query` 引入 `spring-ai-model`
  - **文件**：`aimi-rag-query/pom.xml`
  - 仅引入 API 依赖（`Message` / `Prompt`），不引入 provider starter

---

## 2. 契约改造（aimi-rag-query）

- [x] 2.1 `GenerationStep` 泛型改为 `List<Message>` → `String`
  - **文件**：`aimi-rag-query/src/main/java/.../query/step/generation/GenerationStep.java`
  - `getInputType()` 返回 `List.class`；`doProcess` 返回 `String`
  - 移除对 `RetrievalResult` / `QueryResult` 的引用

---

## 3. 实现改造（aimi-rag-generation）

- [x] 3.1 新增 `ChatGenerationStep`
  - **文件**：`aimi-rag-generation/src/main/java/.../generation/step/ChatGenerationStep.java`
  - 接受任意 `ChatModel`，`doProcess` 执行 `chatModel.call(new Prompt(input))` 并返回答案文本
  - 默认输入 resolver：从 `messages` 读取 `List<Message>`
  - 默认输出 resolver：将答案写入 `answer`
  - 保留 `ConfigurableBuilder`（`apiKey` / `baseUrl` / `model`）

- [x] 3.2 删除 `OpenAiGenerationStep`
  - 由 `ChatGenerationStep` 取代，避免同类能力重复

- [x] 3.3 删除 RAG 专用 resolver
  - 删除 `RagMessageInputResolver`、`QueryResultOutputResolver`
  - 解除 generation 模块对 `RetrievalResult` / `QueryResult` 的依赖

- [x] 3.4 移除 builder 中的 prompt 模板配置
  - 移除 `systemPrompt` / `userPromptTemplate`（BREAKING），prompt 组装交由调用方

---

## 4. 验证

- [x] 4.1 全量编译通过（`mvn test-compile`）
- [x] 4.2 单元测试通过（core / ingest / embedding / query / generation）
- [x] 4.3 补充 `ChatGenerationStep` 单元测试
  - 使用 mock `ChatModel` 验证：默认读取 `messages`、默认写入 `answer`、自定义 resolver 生效

---

## 5. 后续清理（本变更外）

- [ ] 5.1 评估删除 `QueryResult`
  - 该模型已无引用；是否保留为对外 API 待定
