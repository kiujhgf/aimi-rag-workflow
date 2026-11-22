## ADDED Requirements

### Requirement: SourceFile 输入模型

The aimi-rag-mineru module SHALL provide a `SourceFile` model that pairs a file name with its raw bytes for multipart upload.

#### Scenario: SourceFile 属性

- **WHEN** a `SourceFile` is created with fileName `"document.pdf"` and content bytes
- **THEN** `getFileName()` returns `"document.pdf"` and `getContent()` returns those bytes

### Requirement: MineruClient multipart 上传

The `MineruClient` SHALL upload files via multipart to the MinerU `/file_parse` endpoint, submitting all parse options as multipart form fields, and return the raw response body.

#### Scenario: 上传文件并返回响应

- **WHEN** `parse(List.of(sourceFile))` is called
- **THEN** an HTTP POST is sent to `{baseUrl}/file_parse` with multipart field `files` carrying the file bytes
- **AND** the configured options are sent as form fields: `backend`(默认 `pipeline`)、`lang_list`(默认 `ch`)、`return_md`(固定 `true`)，以及可选的 `parse_method`、`formula_enable`、`table_enable`、`image_analysis`、`start_page_id`、`end_page_id`
- **AND** the raw response body string is returned

#### Scenario: 非 2xx 响应抛异常

- **WHEN** the server responds with a non-2xx status (e.g. 422)
- **THEN** a `MineruClientException` is thrown

### Requirement: 响应解析器

The `MineruResponseParser` interface SHALL convert a MinerU response body into a `List<String>` of parsed text, with a markdown default implementation.

#### Scenario: 提取 markdown 文本

- **WHEN** `MarkdownResponseParser.parse(body, sources)` receives a response whose `results.<文件名>.md_content` contains markdown text
- **THEN** it returns a `List<String>` of those texts aligned with the input `sources` order
- **AND** `results` 的 key 优先按「去扩展名的文件名」匹配，其次按完整文件名匹配

#### Scenario: 解析失败响应

- **WHEN** the response has a non-null `error` or a `status` other than `completed`
- **THEN** a `MineruParseException` is thrown with the failure detail
- **AND** the `results` object is missing entirely, a `MineruParseException` is thrown

### Requirement: MineruParseStep

The `MineruParseStep` SHALL parse `List<SourceFile>` into `List<String>` and write the result under the `texts` key.

#### Scenario: Step 类型与类别

- **WHEN** `MineruParseStep` is queried
- **THEN** `getType()` returns `StepType.PARSING`
- **AND** `getCategory()` returns the configured `FlowCategory` (default `INGEST`)

#### Scenario: 默认输出 key

- **WHEN** the step finishes successfully
- **THEN** the resulting `List<String>` is written to the `texts` key in `FlowContext`
