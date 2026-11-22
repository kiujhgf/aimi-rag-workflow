## ADDED Requirements

### Requirement: TextToEmbed 模型

The aimi-rag-ingest module SHALL provide a `TextToEmbed` model that pairs a vector name with its text to embed. An empty or null name denotes the primary vector; a non-empty name denotes an additional vector.

#### Scenario: TextToEmbed 属性

- **WHEN** a `TextToEmbed` is created with name `"image"` and text `"图片描述"`
- **THEN** `getName()` returns `"image"` and `getText()` returns `"图片描述"`

#### Scenario: 主向量名称为空

- **WHEN** a `TextToEmbed` is created with a null or empty name
- **THEN** `getName()` returns null or empty, denoting the primary vector

### Requirement: Chunk 向量字段

The `Chunk` model SHALL expose a list of `TextToEmbed` instances, defaulting to an empty immutable list when not provided, while preserving the existing two-argument constructor.

#### Scenario: 默认空列表

- **WHEN** a `Chunk` is created with only content and metadata
- **THEN** `getTextsToEmbed()` returns an empty immutable list

#### Scenario: 携带向量

- **WHEN** a `Chunk` is created with content, metadata, and a non-empty `List<TextToEmbed>`
- **THEN** `getTextsToEmbed()` returns that list in order and the returned list is immutable
