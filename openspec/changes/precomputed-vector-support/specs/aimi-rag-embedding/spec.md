## ADDED Requirements

### Requirement: 统一嵌入命名文本

The `IngestOpenAiEmbeddingStep` SHALL collect the text of every `TextToEmbed` across all chunks and embed them through the embedding API in a single batch.

#### Scenario: 收集并嵌入所有文本

- **WHEN** processing chunks whose `TextToEmbed`s have texts `["主", "图片描述"]`
- **THEN** the embedding model is invoked once with both texts

### Requirement: 主向量落到 Embedding

The step SHALL treat the vector generated from the `TextToEmbed` whose name is empty (or null) as the primary vector and set the produced `Embedding`'s vector to it.

#### Scenario: name 为空的主向量

- **WHEN** a chunk has a `TextToEmbed` with empty name whose text embeds to vector `v`
- **THEN** the produced `Embedding`'s vector equals `v`

#### Scenario: 无主向量时为 null

- **WHEN** a chunk has no `TextToEmbed` with an empty name
- **THEN** the produced `Embedding`'s vector is null

### Requirement: 额外向量写入 name_vector

The step SHALL write each vector generated from a `TextToEmbed` whose name is non-empty into the chunk metadata under the key `<name>_vector` as a `List<Float>`.

#### Scenario: 额外向量命名

- **WHEN** a chunk has `TextToEmbed`s named `"image"` and `"audio"`
- **THEN** the chunk metadata contains `image_vector` and `audio_vector`, each as a `List<Float>` equal to the respective embedded vector
