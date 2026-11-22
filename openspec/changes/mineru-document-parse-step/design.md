# 设计文档：MinerU 文档解析 Step

## 1. 设计目标

新增 `MineruParseStep`，把二进制文件（PDF/Word/图片等）通过 MinerU 服务器解析为
纯文本，产出 `List<String>`，可复用于 ingest / query 等任意流程（产出可衔接 `ChunkingStep`）。

## 2. 模块与包结构

新模块 `aimi-rag-mineru`：

```
aimi-rag-mineru/src/main/java/io/github/aimi/rag/workflow/mineru/
├── model/
│   └── SourceFile.java            # 输入模型：fileName + content(byte[])
├── client/
│   ├── MineruClient.java          # HTTP 客户端：multipart 上传，返回响应 JSON
│   └── MineruClientException.java # 非 2xx / IO 异常包装
├── resolver/
│   ├── MineruResponseParser.java    # 响应解析器接口
│   ├── MarkdownResponseParser.java  # 默认实现：提取 markdown 文本
│   └── MineruParseException.java    # 解析异常包装
└── step/
    └── MineruParseStep.java       # Step 实现
```

## 3. 数据模型

### 3.1 SourceFile

```java
public class SourceFile {
    private final String fileName;   // multipart 文件名
    private final byte[] content;    // 文件字节

    public SourceFile(String fileName, byte[] content) { ... }
    public String getFileName() { ... }
    public byte[] getContent() { ... }
}
```

### 3.2 （无其他模型）

解析结果直接以 `List<String>` 表达，不额外引入单文件结果模型。

## 4. MineruClient

职责：构造 multipart 请求，调用 MinerU 同步接口，返回原始响应 JSON 字符串。

**真实服务契约**（MinerU 3.2.1，实测于 `<mineru-host>:8000`）：

- 接口：`POST /file_parse`（同步，服务端解析完成后直接返回）
- 所有选项以 **multipart form 字段** 提交，不是 URL query：

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `files` | 文件（可多个） | 必填 | 待解析文件 |
| `backend` | string | `pipeline` | `pipeline` / `vlm-auto-engine` / `vlm-http-client` / `hybrid-auto-engine` / `hybrid-http-client` |
| `lang_list` | string[] | `["ch"]` | 语言列表，可重复字段 |
| `parse_method` | string | 服务端 `auto` | `auto` / `txt` / `ocr` |
| `formula_enable` | bool | 服务端 `true` | 公式识别 |
| `table_enable` | bool | 服务端 `true` | 表格识别 |
| `image_analysis` | bool | 服务端 `true` | 图片分析 |
| `start_page_id` / `end_page_id` | int | `0` / `99999` | 页范围 |
| `return_md` | bool | `true` | 固定传 `true`，保证返回 markdown |

- 基于 `spring-web` 的 `RestTemplate`，`FormHttpMessageConverter` 负责 multipart 编码
- **说明**：`spring-web` 是纯 HTTP 客户端抽象层，compile 依赖只有 `spring-core` + `spring-beans` + `micrometer-observation`，**不含 Servlet API / Tomcat / Boot starter**，可安全用于非 Web 项目
- 可配置项：`baseUrl`、`endpoint`（默认 `/file_parse`）、`backend`、`langList`、`parseMethod`、`formulaEnable`、`tableEnable`、`imageAnalysis`、`startPageId`、`endPageId`、`connectTimeout`（默认 30s）、`readTimeout`（默认 600s）

```java
public String parse(List<SourceFile> files) throws MineruClientException {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    for (SourceFile f : files) {
        body.add("files", new NamedByteArrayResource(f));
    }
    body.add("backend", backend);
    for (String lang : langList) body.add("lang_list", lang);
    if (parseMethod != null) body.add("parse_method", parseMethod);
    body.add("return_md", "true");
    // formula_enable / table_enable / image_analysis / start_page_id / end_page_id 按需追加

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    try {
        return restTemplate.postForObject(baseUrl + endpoint,
                new HttpEntity<>(body, headers), String.class);
    } catch (HttpClientErrorException | HttpServerErrorException e) {
        throw new MineruClientException("MinerU responded with " + e.getStatusCode().value(), e);
    } catch (RestClientException e) {
        throw new MineruClientException("MinerU request failed: " + e.getMessage(), e);
    }
}
```

### RestTemplate 配置

`MineruClient` 通过 Builder 接收一个 `RestTemplate` 实例；若调用方不提供，内部创建默认配置（`SimpleClientHttpRequestFactory`，connect/read 超时可配），底层使用 JDK `HttpURLConnection`，无额外 transport 依赖。

## 5. MineruResponseParser

```java
public interface MineruResponseParser {
    List<String> parse(String responseBody, List<SourceFile> sources) throws MineruParseException;
}
```

默认实现 `MarkdownResponseParser`：

1. 用 Jackson 将响应解析为树（`JsonNode`）
2. `error` 非空或 `status` 非 `completed` → 抛 `MineruParseException`
3. 取 `results` 对象（缺失则抛异常），按 `sources` 顺序查找 `results.<去扩展名文件名>`，回退完整文件名
4. 从结果节点取 `md_content`（兼容字符串与嵌套对象，回退 `markdown` / `content` / `text`）
5. 返回 `List<String>`（与输入 `sources` 顺序对齐；缺失的源文件跳过）

响应字段名通过常量集中在解析器内，便于替换与适配不同部署。

## 6. MineruParseStep

```java
public class MineruParseStep extends AbstractStep<List<SourceFile>, List<String>> {
    // Builder 模式，类似 IngestOpenAiEmbeddingStep
    // getName() = "mineru-parse"
    // getType() = StepType.PARSING
    // getCategory() = 可配置的 FlowCategory（Builder.category(...) 设置，默认 INGEST）
    // getInputType() = List.class

    @Override
    protected List<String> doProcess(List<SourceFile> input, FlowContext ctx)
            throws StepExecuteException {
        String body = client.parse(input);
        return responseParser.parse(body, input);
    }
}
```

默认 resolver：

- input：`DefaultInputResolver.ByKeyResolver<>("sourceFiles", List.class, "mineru-parse")`
- output：`DefaultOutputResolver<>("texts")`（对齐 `FixedSizeChunkingStep` 默认输入 key）

## 7. 通用性

`MineruParseStep` 不绑定 ingest；通过 Builder 传入 `FlowCategory` 即可在 ingest / query 等流程复用：

```
List<SourceFile> → MineruParseStep → texts(List<String>) → FixedSizeChunkingStep → chunks → ...
```

## 8. 异常处理

- HTTP 非 2xx（如 422）→ `MineruClientException` → `StepExecuteException`
- HTTP `IOException` / 超时 → `MineruClientException` → `StepExecuteException`
- 响应 `error` 非空 / `status` 非 completed / 缺 `results` / JSON 解析失败 → `MineruParseException` → `StepExecuteException`
- 以上均走 `AbstractStep` 既有重试机制

## 9. 改动清单

#### 9.1 文件改动

| 文件 | 改动 |
|------|------|
| `aimi-rag-core/.../model/StepType.java` | 新增 `PARSING` |
| `pom.xml` | 注册 `aimi-rag-mineru` 模块 |
| `aimi-rag-mineru/pom.xml` | 新增（见 9.2） |
| `mineru/model/SourceFile.java` | 新增 |
| `mineru/client/MineruClient.java` | 新增 |
| `mineru/client/MineruClientException.java` | 新增 |
| `mineru/resolver/MineruResponseParser.java` | 新增 |
| `mineru/resolver/MarkdownResponseParser.java` | 新增 |
| `mineru/resolver/MineruParseException.java` | 新增 |
| `mineru/step/MineruParseStep.java` | 新增 |

#### 9.2 dependency 清单

版本由根 pom 导入的 `spring-boot-dependencies` BOM 托管，子模块无需显式写 version；`aimi-rag-core` 走 `${project.version}`。

| groupId | artifactId | scope | 用途 |
|---------|-----------|-------|------|
| `io.github.aimi.rag.workflow` | `aimi-rag-core` | compile | Step 基类 / StepType / FlowCategory |
| `org.springframework` | `spring-web` | compile | RestTemplate / MultipartBodyBuilder / HttpHeaders |
| `com.fasterxml.jackson.core` | `jackson-databind` | compile | MinerU 响应 JSON 解析 |
| `org.junit.jupiter` | `junit-jupiter` | test | 单元测试 |
| `org.springframework` | `spring-test` | test | MockRestServiceServer（multipart 测试） |
| `org.mockito` | `mockito-core` | test | Step 单元测试打桩 |

**注意**：`spring-web` compile 依赖只有 `spring-core` + `spring-beans` + `micrometer-observation`，**不含 Servlet API / Tomcat / spring-boot-starter-web**，可安全用于非 Web 项目。

## 10. 风险与权衡

| 风险 / 权衡 | 缓解措施 |
|------|---------|
| 不同 MinerU 版本/部署的响应结构差异 | 解析器接口隔离，字段名常量集中，可替换实现 |
| 引入 spring-web | 纯 HTTP 客户端层，不含 Servlet/Tomcat/Boot，不会把项目变成 Web 服务端 |
| 同步接口耗时长（解析时间随文件增长） | `readTimeout` 默认 600s 可配；需更快响应可改用异步 `/tasks` 接口 |
| `backend` 取值影响精度与算力 | 默认 `pipeline`（通用、无幻觉）；可切换 `vlm-*` / `hybrid-*` |
| byte[] 全量驻内存 | 由调用方控制文件大小，后续可扩展流式上传 |
