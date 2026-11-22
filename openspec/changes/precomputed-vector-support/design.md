# 设计文档：多向量嵌入支持

## 1. 设计目标

让 `IngestOpenAiEmbeddingStep` 不再只对 `content` 生成单个向量，而是根据
`Chunk` 中声明的 `TextToEmbed` 列表，统一收集待嵌入文本并批量调用嵌入接口，
按 name 语义把生成的向量分配到主向量与额外向量，支持多模态、多向量场景。

## 2. 核心设计

### 2.1 数据模型

`TextToEmbed`（`aimi-rag-ingest`）：

```java
public class TextToEmbed {
    private final String name;   // 为空/null 表示主向量(_vector)；非空表示额外向量(<name>_vector)
    private final String text;   // 待嵌入文本
}
```

`Chunk` 新增待嵌入文本字段：

```java
public class Chunk implements StorageItem {
    private final String content;
    private final Map<String, Object> metadata;
    private final List<TextToEmbed> textsToEmbed;   // 新增，默认空 list

    public Chunk(String content, Map<String, Object> metadata) {
        this(content, metadata, List.of());    // 旧构造器保持兼容
    }

    public Chunk(String content, Map<String, Object> metadata, List<TextToEmbed> textsToEmbed) {
        this.textsToEmbed = List.copyOf(textsToEmbed);
    }

    public List<TextToEmbed> getTextsToEmbed() { return textsToEmbed; }
}
```

### 2.2 命名规则与单一真源

| name | 语义 | 落点 | 真源 |
|------|------|------|------|
| 空 / null | 主向量 | `_vector` | `Embedding.vector` |
| 非空 | 额外向量 | `<name>_vector` | `metadata` |

要点：

- **主向量**：`name` 为空的 TextToEmbed 的文本经 API 嵌入后写入 `Embedding.vector`，converter 据此写 `_vector`
- **额外向量**：`name` 非空的 TextToEmbed 的文本经 API 嵌入后，转 `List<Float>` 写入 `metadata["<name>_vector"]`
- **无主向量**：若 chunk 没有 `name` 为空的 TextToEmbed，主向量为 null（由调用方保证提供空 name 主向量）
- **content 不参与嵌入**：content 仅作为存储/检索文本

### 2.3 doProcess 逻辑

```
1. 展平所有 chunk 的 TextToEmbed.text，得到 texts 列表
2. texts 非空时统一 batchEmbed(texts, batchSize) 生成向量
3. 按序回填每个 chunk：
     for item in chunk.getTextsToEmbed():
         vec = 下一个嵌入结果
         若 item.name 为空：primary = vec（取第一个空 name）
         否则：metadata[item.name + "_vector"] = toFloatList(vec)
     result.add(new Embedding(chunk, primary))
```

### 2.4 与既有机制的协作

| 机制 | 关系 |
|------|------|
| `DefaultStorageItemConverter` | 无需改动；`_vector` 来自 `Embedding.vector`，`<name>_vector` 经 metadata 透传 |
| `Embedding` 模型 | 无需改动；`vector` 字段作为主向量唯一真源 |
| `StorageItem` 接口 | 无需改动；metadata 透传链路复用 |
| 批量嵌入 | 所有文本统一展平后批量调用，保持吞吐 |

### 2.5 ES mapping 契约

多向量字段 `<name>_vector` 需要 ES 索引预先定义对应的 `dense_vector` mapping。
本变更不自动生成 mapping，调用方需在创建索引时显式声明所有向量字段；否则
`<name>_vector` 会被当作普通字段存储，无法参与 KNN 检索。

## 3. 改动清单

| 文件 | 改动 |
|------|------|
| `ingest/model/TextToEmbed.java` | 新增，字段 `name` + `text`（String） |
| `ingest/model/Chunk.java` | 新增 `List<TextToEmbed> textsToEmbed` 字段、新构造器、`getTextsToEmbed()` |
| `embedding/step/IngestOpenAiEmbeddingStep.java` | 重写 `doProcess()`：展平文本、批量嵌入、按 name 分配 |

## 4. 风险与权衡

| 风险 / 权衡 | 缓解措施 |
|------|---------|
| `float[]` 直接写 metadata 导致 ES 序列化失败 | 统一 `toFloatList()` 转 `List<Float>` 后再写回 |
| 无空 name 的 TextToEmbed 导致主向量缺失 | 主向量为 null，契约要求调用方提供空 name 主向量 |
| 旧 `Chunk(content, metadata)` 调用方受影响 | 保留旧构造器，委托为空 `textsToEmbed` |
| `<name>_vector` 缺少 ES mapping | 契约明确要求调用方预先建 mapping |
| `TextToEmbed.name` 重复导致字段名冲突 | 要求调用方保证非空 name 唯一 |
