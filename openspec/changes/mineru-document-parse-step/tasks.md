# 开发计划：MinerU 文档解析 Step

## 概述

新增 `aimi-rag-mineru` 模块，提供 `MineruParseStep`，通过 multipart 上传文件字节到
MinerU `/file_parse`（MinerU 3.2.1 实测契约），返回解析文本 `List<String>`，衔接现有 `ChunkingStep`。

**模块**：aimi-rag-mineru（新增）、aimi-rag-core（StepType）

---

## 1. core：StepType 新增 PARSING

- [x] 1.1 `aimi-rag-core/.../model/StepType.java` 新增 `PARSING`

## 2. 新模块骨架

- [x] 2.1 根 `pom.xml` 注册 `aimi-rag-mineru` 模块
- [x] 2.2 `aimi-rag-mineru/pom.xml`：依赖 core、spring-web、jackson-databind；测试依赖 junit-jupiter、spring-test、mockito-core

## 3. 数据模型

- [x] 3.1 `model/SourceFile.java`：fileName + byte[] content

## 4. HTTP 客户端

- [x] 4.1 `client/MineruClientException.java`：非 2xx / IO 异常包装
- [x] 4.2 `client/MineruClient.java`：RestTemplate 提交 multipart **form 字段**（files、backend、lang_list、parse_method、formula_enable、table_enable、image_analysis、start_page_id、end_page_id、return_md），默认 backend=pipeline；connect/read 超时走 SimpleClientHttpRequestFactory

## 5. 响应解析

- [x] 5.1 `resolver/MineruParseException.java`
- [x] 5.2 `resolver/MineruResponseParser.java` 接口
- [x] 5.3 `resolver/MarkdownResponseParser.java` 默认实现（读 `results.<去扩展名文件名>.md_content`，按 sources 顺序对齐；error 非空 / status 非 completed / 缺 results 抛异常）

## 6. Step 实现

- [x] 6.1 `step/MineruParseStep.java`：Builder（接收 MineruClient）+ ConfigurableBuilder（内部构造 Client），category 可配置（默认 INGEST），默认 inputResolver("sourceFiles")/outputResolver("texts")

## 7. 单元测试

- [x] 7.1 `MineruClientTest`：路径 `/file_parse`、multipart form 字段与默认值、多文件 part、非 2xx 抛异常、空列表校验
- [x] 7.2 `MarkdownResponseParserTest`：`results.<name>.md_content` 解析与顺序对齐、完整文件名匹配、嵌套 md_content、error/status/缺 results/无效 JSON
- [x] 7.3 `MineruParseStepTest`：name/type/category、默认输出 key、自定义输出 key、MineruClientException 转 StepExecuteException、ConfigurableBuilder 配置透传、baseUrl 缺失校验

## 8. 真实服务联调

- [x] 8.1 连通性：`<mineru-host>:8000` TCP + `/health`（MinerU 3.2.1）
- [x] 8.2 端到端：真实客户端上传测试 PDF，1.77s 返回并正确解析出 `Hello MinerU Test`
