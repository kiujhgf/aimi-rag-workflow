# Proposal: MinerU 文档解析 Step

## 背景

当前 ingest 链路以文本为起点：`InputStep` 产出 `List<String>`，随后进入
`ChunkingStep → EmbeddingStep → StorageStep`。但真实业务中，原始输入是 PDF、
Word、图片等**二进制文件**，缺少「文件 → 文本」的解析能力。

项目已部署 MinerU 文档解析服务器（实测 3.2.1），可通过其同步接口 `POST /file_parse`
以 `multipart/form-data` 上传文件字节及解析选项，同步返回
`results.<文件名>.md_content` 形式的解析结果。本变更新增一个 `MineruParseStep`，把该服务器接入工作流。

## 目标

- 新增 `aimi-rag-mineru` 模块，隔离 MinerU 的 HTTP 调用与模型
- 提供 `MineruParseStep`：输入 `List<SourceFile>`（文件名 + 字节），输出 `List<String>`（解析文本）
- 输出默认写入 `texts`，可直接衔接现有 `FixedSizeChunkingStep`
- 作为通用解析 Step，不绑定 ingest，可在 ingest / query 等任意流程复用（`FlowCategory` 可配置）
- 请求与响应解析均可配置与替换，适配不同 MinerU 部署

## 范围

### 包含

- `aimi-rag-mineru` 新模块：`SourceFile` 模型、`MineruClient`（HTTP multipart）、`MineruResponseParser`（接口 + markdown 默认实现）、`MineruParseStep`
- `aimi-rag-core`：`StepType` 新增 `PARSING`
- 单元测试

### 不包含

- MinerU 异步任务轮询（仅使用同步 `/file_parse`，异步 `/tasks` 接口暂不接入）
- 多格式产物（docx/html/latex/zip）下载与解包
- 解析后图片/表格的本地落盘
- 认证（当前部署无鉴权）

## 能力 (Capabilities)

### 新增的能力 (Added Capabilities)

- `aimi-rag-mineru`：MinerU 文档解析 Step 及配套 client/parser

### 修改的能力 (Modified Capabilities)

- `aimi-rag-core`：`StepType` 新增 `PARSING`

对应 delta spec 文件：

- `specs/aimi-rag-mineru/spec.md`
- `specs/aimi-rag-core/spec.md`

## 关键决策

| 决策       | 选择                                                     | 原因                                |
| -------- | ------------------------------------------------------ | --------------------------------- |
| 模块       | 新建 `aimi-rag-mineru`                                   | 隔离第三方 HTTP 依赖与模型，保持核心模块纯净     |
| HTTP 客户端 | `spring-web` 的 `RestTemplate` + `MultipartBodyBuilder` | Spring Framework HTTP 抽象层（`spring-web`）不含 Servlet/Tomcat/Boot，可安全用于非 Web 项目 |
| 请求方式     | `POST /file_parse`，`files` 字段 multipart 上传，解析选项同为 form 字段 | MinerU 同步接口，一次请求返回结果；选项（backend/lang_list 等）非 query 参数 |
| 输入模型     | `List<SourceFile>`（fileName + byte\[]）                 | multipart 需文件名与字节                 |
| 输出模型     | `List<String>`，默认写入 `texts`                            | 直接衔接 `FixedSizeChunkingStep` 默认输入 |
| 响应解析     | `MineruResponseParser` 接口 + markdown 默认实现（读 `results.<name>.md_content`） | 应对不同部署的响应结构差异，可替换                 |
| StepType | 新增 `PARSING`                                           | 解析是独立的标准步骤类型，语义清晰              |
| 认证       | 不内置（当前无鉴权）                                             | YAGNI，后续按需扩展                      |

## 影响评估

- **aimi-rag-mineru**：新增模块
- **aimi-rag-core**：`StepType` 新增枚举值 `PARSING`
- **aimi-rag-ingest / embedding / es / query**：无代码变更

## 风险

| 风险                                     | 缓解措施                              |
| -------------------------------------- | --------------------------------- |
| MinerU 响应结构/接口路径随版本与部署而异（已按 3.2.1 实测校准） | 解析器抽象为接口，字段名常量集中，可替换实现 |
| multipart 大文件内存占用                      | 输入为 `byte[]`，由调用方控制文件大小；后续可扩展流式上传 |
| 同步接口解析耗时较长                             | `readTimeout` 默认 600s 可配，超时抛异常，走既有重试机制 |

