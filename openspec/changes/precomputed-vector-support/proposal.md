# Proposal: 多向量嵌入支持

## 背景

当前 `IngestOpenAiEmbeddingStep.doProcess()` 硬编码为：提取每个 `Chunk` 的 `content`
文本，统一调用 OpenAI 嵌入接口生成单个主向量，再包装为 `Embedding`。这带来两个问题：

1. 一个 Chunk 只能产出单个 `_vector` 向量，无法表达多向量场景（如文本向量 + 图像描述向量并存）
2. 需要嵌入的文本来源被固定在 `content`，无法指定额外的待嵌入文本

## 目标

- `Chunk` 新增 `List<TextToEmbed>` 字段，承载一个或多个「命名待嵌入文本」
- `TextToEmbed` 由 `name` + `text` 组成：`text` 为待嵌入文本，`name` 决定向量落点
- 主向量（`name` 为空的 TextToEmbed 的文本）生成后作为 `Embedding.vector`
- 额外向量（`name` 非空的 TextToEmbed 的文本）生成后按 `<name>_vector` 写入 metadata
- 所有向量统一通过 OpenAI 嵌入接口生成

## 范围

### 包含

- `aimi-rag-ingest`：`TextToEmbed` 模型字段改为 `text`（String）；`Chunk` 新增向量字段
- `aimi-rag-embedding`：`IngestOpenAiEmbeddingStep` 统一收集待嵌入文本并批量调用 API，按 name 分配向量
- 单元测试

### 不包含

- ES 索引多向量 `dense_vector` mapping 的自动生成（多向量检索另立变更）
- 多向量检索 / 混合检索逻辑
- `QueryOpenAiEmbeddingStep` 的类似改造（查询侧多向量）

## 能力 (Capabilities)

### 修改的能力 (Modified Capabilities)

- `aimi-rag-ingest`：`Chunk` 新增向量字段，`TextToEmbed` 承载待嵌入文本
- `aimi-rag-embedding`：`IngestOpenAiEmbeddingStep` 统一嵌入命名文本并按 name 分配向量

对应 delta spec 文件：

- `specs/aimi-rag-ingest/spec.md`
- `specs/aimi-rag-embedding/spec.md`

## 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 待嵌入文本载体 | `Chunk` 新增 `List<TextToEmbed>`，`TextToEmbed.text` 为待嵌入文本 | 类型安全、显式，不混入 metadata |
| 主向量判定 | `TextToEmbed.name` 为空即主向量 | 语义驱动，不依赖数量或顺序 |
| 向量命名 | 主向量 → `_vector`；额外向量 → `<name>_vector` | 与 ES `dense_vector` 字段约定一致 |
| 单一真源 | 主向量走 `Embedding.vector`，额外向量走 metadata | 消除 `_vector` 双写，converter 不变 |
| 向量生成 | 所有文本统一走 OpenAI API | 无预生成向量场景，保持一致性 |
| content 角色 | content 不再参与 embedding，仅作为存储/检索文本 | 主向量文本显式由空 name 的 TextToEmbed 指定 |

## 影响评估

- **aimi-rag-ingest**：修改 `Chunk`、`TextToEmbed`
- **aimi-rag-embedding**：修改 `IngestOpenAiEmbeddingStep`
- **aimi-rag-es**：无代码变更；多向量 `<name>_vector` 需调用方预先建 `dense_vector` mapping

## 风险

| 风险 | 缓解措施 |
|------|---------|
| `float[]` 直接写入 metadata 导致 ES 序列化失败 | 写回 metadata 前统一转 `List<Float>` |
| 无空 name 的 TextToEmbed 导致主向量缺失 | 主向量为 null，由调用方保证提供空 name 主向量 |
| `Chunk` 构造器改动影响现有调用方 | 保留旧构造器，新增重载，默认空 list |
| `<name>_vector` 缺少 ES mapping | 契约明确要求调用方预先建 mapping |
