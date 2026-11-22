# 开发计划：多向量嵌入支持

## 概述

让 `IngestOpenAiEmbeddingStep` 根据 `Chunk` 中的 `TextToEmbed` 列表（name + 待嵌入文本），
统一收集文本并批量调用 OpenAI 嵌入接口，按 name 语义分配：主向量 → `Embedding.vector`，
额外向量 → `metadata["<name>_vector"]`。

**模块**：aimi-rag-ingest、aimi-rag-embedding

---

## 1. 数据模型（aimi-rag-ingest）

- [x] 1.1 新增 `TextToEmbed` 模型
  - **文件**：`aimi-rag-ingest/src/main/java/.../ingest/model/TextToEmbed.java`
  - 字段：`name`（String，可为空/null 表示主向量）、`text`（String，待嵌入文本）
  - 构造器 `TextToEmbed(String name, String text)`，提供 `getName()` / `getText()`

- [x] 1.2 `Chunk` 新增待嵌入文本字段
  - **文件**：`aimi-rag-ingest/src/main/java/.../ingest/model/Chunk.java`
  - 新增 `private final List<TextToEmbed> textsToEmbed`，默认空 list
  - 新增三参构造器 `Chunk(content, metadata, textsToEmbed)`，旧构造器委托为 `List.of()`
  - `getTextsToEmbed()` 返回不可变 list（`List.copyOf`）

---

## 2. embedding step 改造（aimi-rag-embedding）

- [x] 2.1 重写 `doProcess()`：展平文本并批量嵌入
  - **文件**：`aimi-rag-embedding/src/main/java/.../embedding/step/IngestOpenAiEmbeddingStep.java`
  - 收集所有 chunk 的 `TextToEmbed.text`，统一 `batchEmbed` 生成向量

- [x] 2.2 按 name 分配向量
  - 空 name → 主向量（`Embedding.vector`）；非空 name → `metadata["<name>_vector"]`（`List<Float>`）

- [x] 2.3 移除 `PrecomputedVectorWriter` 组件
  - 预生成（跳过 API）语义不再适用，删除该组件，分配逻辑内联到 `doProcess`

---

## 3. 单元测试

- [x] 3.1 `Chunk` 待嵌入文本字段测试
  - 旧构造器 `getTextsToEmbed()` 返回空不可变 list
  - 三参构造器 `getTextsToEmbed()` 返回传入顺序、不可变

- [x] 3.2 主向量测试
  - 空 name 的 TextToEmbed 文本经 API 嵌入后作为主向量

- [x] 3.3 额外向量测试
  - 非空 name 的 TextToEmbed 文本经 API 嵌入后写入 `metadata["<name>_vector"]`，为 `List<Float>`

- [x] 3.4 无 TextToEmbed 测试
  - 无 TextToEmbed 时主向量为 null，不调用 API
