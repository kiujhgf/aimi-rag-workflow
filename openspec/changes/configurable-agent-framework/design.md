## Context

aimi-rag-workflow 是一个父项目，整合多个子模块的能力，提供上层抽象供其他项目集成使用。

项目结构：

- **aimi-rag-workflow**: 父项目，提供工作流配置框架（SDK）
- **aimi-rag-core**: 公共抽象层，提供 Step/Flow 接口、数据模型、注解、默认实现
- **aimi-rag-ingest**: 子项目，数据处理和向量数据库摄入
- **aimi-rag-es**: 子项目，Elasticsearch存储
- **aimi-rag-open-ai**: 子项目，封装 Spring AI OpenAI 依赖，排除不必要 AutoConfiguration
- **aimi-rag-embedding**: 子项目，向量化服务（依赖 aimi-rag-open-ai 而非直接依赖 spring-ai）

技术栈选择：

- **Java 21**: 最新 LTS 版本
- **Spring Boot 3.5.15-SNAPSHOT**: 最新框架版本
- **Jackson**: JSON/YAML配置处理

## Goals / Non-Goals

**Goals:**

- aimi-rag-workflow作为父项目整合子项目能力
- 子项目提供类和方法（SDK），供其他项目集成使用
- 上层抽象，便于其他项目集成

**Non-Goals:**

- 不提供HTTP API（非微服务）
- 不提供前端界面
- 不提供LLM调用实现

## Decisions

### 1. 项目架构

**决策**: 父项目 + 子项目结构，SDK方式提供能力

```
aimi-rag-workflow (父项目)
    │
    ├── aimi-rag-core (公共抽象层)
    │   └── Input<T>, Step<I,O>, StepType, FlowCategory, @StepOrder, FlowException
    │
    ├── aimi-rag-ingest (子项目)
    │   └── depends on: aimi-rag-core
    │   └── Chunk, Embedding, StorageItem, IngestFlow, Steps, StorageItemConverter
    │
    ├── aimi-rag-es (子项目)
    │   └── depends on: aimi-rag-ingest
    │   └── EsClient, EsStorageStep, IndexStrategy
    │
    ├── aimi-rag-open-ai (子项目)
    │   └── depends on: spring-ai-starter-model-openai
    │   └── 排除 Audio/Chat/Image/Moderation AutoConfiguration
    │   └── 暴露 EmbeddingModel Bean
    │
    └── aimi-rag-embedding (子项目)
        └── depends on: aimi-rag-ingest, aimi-rag-open-ai
        └── IngestOpenAiEmbeddingStep
```

**理由**: aimi-rag-core 作为公共抽象层，子项目只依赖 core，不互相依赖，保持模块独立

### 2. 使用方式

**决策**: 提供声明式 Flow（SDK），而非HTTP API

开发者只需定义 Flow Bean，然后注入使用。当前聚焦 IngestFlow：

输入类型由 InputStep 决定，编译期类型安全：

```java
// StringInputStep → IngestFlow<List<String>>
@Autowired
private IngestFlow<List<String>> textIngest;

// 调用 — 通过 Input 携带 payload 和 headers
IngestResult r1 = textIngest.execute(
    Input.of(List.of("文档1", "文档2"), Map.of("chunkSize", 500)));
```

**理由**: SDK方式更灵活，无网络开销，声明式 Flow 便于嵌入式集成

### 3. 配置系统

**决策**: 目前以 Java Config 手动装配为主，后续逐步增加 YAML 自动装配支持

```java
@Configuration
public class IngestFlowConfig {

    // 路径A: 文本存储 (Fixed-Size Chunking)
    @Bean
    public IngestFlow<List<String>> textIngest(EsClient esClient) {
        return IngestFlowBuilder.<List<String>>create()
                .input(new StringInputStep())
                .chunking(FixedSizeChunkingStep.builder(500).overlap(50).build())
                .storage(EsStorageStep.builder(esClient).build())
                .build();
    }

    // 路径B: 向量存储 (Recursive Chunking)
    @Bean
    public IngestFlow<List<String>> vectorIngest(EsClient esClient, EmbeddingModel embeddingModel) {
        return IngestFlowBuilder.<List<String>>create()
                .input(new StringInputStep())
                .chunking(RecursiveCharacterChunkingStep.builder(500).overlap(50).build())
                .embedding(IngestOpenAiEmbeddingStep.builder(embeddingModel).build())
                .storage(EsStorageStep.builder(esClient).dims(1536).build())
                .build();
    }
}
```

**理由**: 用户通过 Builder 按需定制，灵活不冲突

### 4. Flow 架构

**决策**: 参考 Spring Integration 的设计思路，各模块自定 Flow 接口

core 提供 `Input<T>` + `Step<I,O>` + `StepType` + `FlowCategory` + `@StepOrder`，各模块基于此定义自己的 Flow

#### 4.1 核心接口

```java
// Input 信封 — 承载 payload + headers
public class Input<T> {
    public static <T> Input<T> of(T payload) { ... }
    public static <T> Input<T> of(T payload, Map<String, Object> headers) { ... }
    public T getPayload() { ... }
    public Map<String, Object> getHeaders() { ... }
    public Object getHeader(String key) { ... }
    public Input<T> withHeader(String key, Object value) { ... }
}

// Step 接口 — 纯数据变换, I → O
// Step 接口 - 纯数据变换, I → O
public interface Step<I, O> {
    String getName();
    StepType getType();
    default FlowCategory getCategory() { return FlowCategory.INGEST; }
    O process(I input);
    default boolean canProcess(I input) { return true; }
}

// StepType 枚举 — 按 step 具体角色分类
public enum StepType {
    INPUT, CHUNKING, EMBEDDING, STORAGE
}

// FlowCategory 枚举 — 按 pipeline 类别分类
public enum FlowCategory {
    INGEST, QUERY, DELTA
}

// @StepOrder 注解 — 自动排序用
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface StepOrder {
    int value();
}

// FlowException — 流程级错误封装
public class FlowException extends RuntimeException {
    public FlowException(String stepName, Throwable cause) { ... }
    public String getStepName() { ... }
}

// ========== IngestFlow（当前已实现）==========
@FunctionalInterface
public interface IngestFlow<T> {
    IngestResult execute(Input<T> input);
}

// ========== QueryFlow（计划中）==========
@FunctionalInterface
public interface QueryFlow {
    QueryResult execute(Input<String> queryText);
}

// ========== DeltaFlow（计划中）==========
@FunctionalInterface
public interface DeltaFlow {
    DeltaResult execute(Input<String> deltaContent);
}
```

#### 4.2 IngestFlow（数据摄入流程）— 当前实现

**路径A**: 文本存储

```
input → chunking → text-storage (ES)
```

**路径B**: 向量存储

```
input → chunking → embedding → vector-storage (ES)
```

IngestFlowBuilder 使用分阶段 Builder 模式，编译期保证步骤顺序：

```java
// 文本路径
IngestFlowBuilder.<List<String>>create()
    .input(new StringInputStep())
    .chunking(chunkingStep)
    .storage(storageStep)
    .build();

// 向量路径
IngestFlowBuilder.<List<String>>create()
    .input(new StringInputStep())
    .chunking(chunkingStep)
    .embedding(embeddingStep)
    .storage(storageStep)
    .build();
```

**Ingest 相关 Step 接口**：

```java
// Input 步骤 — 将 Input<T> 转换为下游类型
public interface InputStep<T> extends Step<Input<T>, ?> { }

// 分块步骤 — List<String> → List<Chunk>
public interface ChunkingStep extends Step<List<String>, List<Chunk>> { }

// Embedding 步骤 — List<Chunk> → List<Embedding>
public interface IngestEmbeddingStep extends Step<List<Chunk>, List<Embedding>> { }

// 存储步骤 — List<StorageItem> → StorageResult
public interface StorageStep<T extends StorageItem> extends Step<List<T>, StorageResult> { }
```

**Chunking 具体策略**：

```java
// Fixed-Size — 固定大小切分，支持 overlap
FixedSizeChunkingStep.builder(500).overlap(50).build()

// Recursive Character — 递归分隔符切分
RecursiveCharacterChunkingStep.builder(500).overlap(50).build()
// 默认分隔符: \n\n → \n → " " → ""
RecursiveCharacterChunkingStep.builder(500).separators(customSeparators).build()
```

**Embedding 实现**：

```java
// IngestOpenAiEmbeddingStep — 使用 EmbeddingModel 直接生成
IngestOpenAiEmbeddingStep.builder(embeddingModel).build()
```

**Storage 通用抽象**：

```java
// StorageItem — Chunk 和 Embedding 的公共接口
public interface StorageItem {
    String getContent();
    Map<String, Object> getMetadata();
}

// StorageItemConverter — StorageItem → ES document map
// 默认实现: DefaultStorageItemConverter (拷贝 metadata + content, Embedding 加 _vector)
// 可通过 EsStorageStep.builder().converter(...) 注入自定义实现
@FunctionalInterface
public interface StorageItemConverter {
    Map<String, Object> toDoc(StorageItem item);
}
```

#### 4.3 QueryFlow（查询检索流程）— 计划中

```
input (查询文本) → embedding → es-search → 返回结果
```

#### 4.4 DeltaFlow（增量更新流程）— 计划中

```
input (增量为空判断) → diff-detect → chunking → embedding → es-upsert
```

#### 4.5 流程对比

| Flow 类型        | 输入                      | 典型步骤                                        | 输出           |
| -------------- | ----------------------- | ------------------------------------------- | ------------ |
| **IngestFlow** | `Input<T>`（文本/文件）     | chunking → [embedding] → storage              | IngestResult |
| **QueryFlow**  | `Input<String>`（查询文本） | embedding → search                          | QueryResult  |
| **DeltaFlow**  | `Input<String>`（增量数据） | diff-detect → chunking → embedding → upsert | DeltaResult  |

### 5. Elasticsearch 客户端选型

**决策**: 使用 Elasticsearch Java Client (`co.elastic.clients:elasticsearch-java`)，版本 `9.4.2`

```xml
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>9.4.2</version>
</dependency>
```

**EsStorageStep** — 统一处理 text/vector，运行时 `instanceof` 判断：

```java
// 文本路径 (dims=0 默认)
EsStorageStep.builder(esClient).build()

// 向量路径
EsStorageStep.builder(esClient).dims(1536).build()

// 自定义 index
EsStorageStep.builder(esClient).dims(768)
    .indexName("my_index")
    .indexStrategy(IndexStrategy.STRICT)
    .converter(customConverter)
    .build()
```

**IndexStrategy 枚举** — 控制索引生命周期：

| 策略 | 行为 |
|---|---|
| `CREATE_IF_NOT_EXISTS` | 不存在则创建（默认） |
| `STRICT` | 必须存在，否则抛异常 |
| `DROP_AND_CREATE` | 先删后建（适合测试） |
| `APPEND_ONLY` | 只追加，不检查 |

### 6. 向量维度处理

**决策**: 维度在 Builder 中指定，`dims` 可选（默认 0 = 无向量）。文本路径传 0，向量路径传实际维度。

```java
// 文本 — dims 不设，默认 0
EsStorageStep.builder(esClient).build()

// 向量 — dims 手动指定
EsStorageStep.builder(esClient).dims(1536).build()
```

### 7. Embedding 框架选型

**决策**: 新增 `aimi-rag-open-ai` 模块封装 Spring AI OpenAI 依赖并隔离 AutoConfiguration 污染。`aimi-rag-embedding` 依赖 `aimi-rag-open-ai`，不直接接触 spring-ai。

**依赖层**：

```
aimi-rag-embedding
    └── depends on: aimi-rag-open-ai
                        └── depends on: spring-ai-starter-model-openai
```

**IngestOpenAiEmbeddingStep** — 直接使用 `EmbeddingModel` 无中间 Client：

```java
public class IngestOpenAiEmbeddingStep implements IngestEmbeddingStep {
    private final EmbeddingModel embeddingModel;

    @Override
    public List<Embedding> process(List<Chunk> input) {
        List<String> texts = input.stream().map(Chunk::getContent).toList();
        var response = embeddingModel.embedForResponse(texts);
        return IntStream.range(0, input.size())
                .mapToObj(i -> new Embedding(input.get(i), response.getResults().get(i).getOutput()))
                .toList();
    }
}
```

### 8. Builder 模式约定

所有有参 Step 统一使用 Builder 创建：

| Step | Builder 必填 | 可选参数 |
|---|---|---|
| `EsStorageStep` | `builder(esClient)` | `.dims(n)`, `.indexName(n)`, `.indexStrategy(s)`, `.converter(c)` |
| `FixedSizeChunkingStep` | `builder(chunkSize)` | `.overlap(n)` |
| `RecursiveCharacterChunkingStep` | `builder(chunkSize)` | `.overlap(n)`, `.separators(list)` |
| `IngestOpenAiEmbeddingStep` | `builder(embeddingModel)` | — |
| `StringInputStep` | 无参 `new` | — |

## Risks / Trade-offs

- \[Trade-off] SDK方式 vs 微服务 → SDK更灵活，但需要项目直接依赖
- \[Version] 版本管理 → 父项目统一版本管理
- \[Trade-off] 显式顺序 vs 自动推断 → 分阶段 Builder 编译期保证路径安全
- \[Trade-off] EsStorageStep 单类 vs 分立 → 单类 + instanceof 更简洁，减少类数量

## Open Questions

- QueryFlow / DeltaFlow 何时实现？
- 是否需要提供Spring Boot Starter自动装配？
- 是否需要支持异步处理？
- ChunkingStep 是否需要添加 Semantic / Document-Aware 策略？
