package io.github.aimi.rag.workflow.mineru.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimi.rag.workflow.mineru.model.SourceFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认响应解析器：从 MinerU 响应的 {@code results.<文件名>.md_content} 中提取 markdown 文本。
 *
 * <p>MinerU 3.x {@code /file_parse} 实际响应结构：</p>
 *
 * <pre>
 * {
 *   "task_id": "...",
 *   "status": "completed",
 *   "backend": "pipeline",
 *   "file_names": ["test"],
 *   "error": null,
 *   "results": {
 *     "test": { "md_content": "..." }
 *   }
 * }
 * </pre>
 *
 * <p>{@code results} 的 key 为去掉扩展名的文件名；解析结果按 {@code sources} 顺序对齐。
 * 若响应的 {@code error} 非空或 {@code status} 不是 completed，抛 {@link MineruParseException}。</p>
 */
public class MarkdownResponseParser implements MineruResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 文本字段候选名，按优先级尝试 */
    private static final String[] TEXT_KEYS = {"md_content", "markdown", "content", "text"};

    @Override
    public List<String> parse(String responseBody, List<SourceFile> sources) throws MineruParseException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new MineruParseException("MinerU response body is empty");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            throw new MineruParseException("Failed to parse MinerU JSON response: " + e.getMessage(), e);
        }

        JsonNode error = root.get("error");
        if (error != null && !error.isNull() && !error.asText().isBlank()) {
            throw new MineruParseException("MinerU task failed: " + error.asText());
        }

        JsonNode status = root.get("status");
        if (status != null && status.isTextual() && !"completed".equalsIgnoreCase(status.asText())) {
            throw new MineruParseException("MinerU task not completed, status=" + status.asText());
        }

        JsonNode results = root.get("results");
        if (results == null || !results.isObject()) {
            throw new MineruParseException("MinerU response missing 'results' object");
        }

        List<String> texts = new ArrayList<>();

        if (sources == null || sources.isEmpty()) {
            results.forEach(node -> {
                String text = extractText(node);
                if (text != null && !text.isBlank()) {
                    texts.add(text);
                }
            });
            return texts;
        }

        Set<String> matchedKeys = new LinkedHashSet<>();
        for (SourceFile source : sources) {
            String key = locateKey(results, source.getFileName());
            if (key == null) {
                continue;
            }
            matchedKeys.add(key);
            String text = extractText(results.get(key));
            if (text != null && !text.isBlank()) {
                texts.add(text);
            }
        }

        return texts;
    }

    /**
     * 定位响应中与源文件对应的 key：优先去扩展名的文件名，其次完整文件名。
     */
    private String locateKey(JsonNode results, String fileName) {
        String stem = stemOf(fileName);
        if (results.has(stem)) {
            return stem;
        }
        if (results.has(fileName)) {
            return fileName;
        }
        return null;
    }

    private static String stemOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    /**
     * 从单个结果节点提取文本，兼容字符串与嵌套对象两种形态。
     */
    private String extractText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (!node.isObject()) {
            return null;
        }
        for (String key : TEXT_KEYS) {
            JsonNode candidate = node.get(key);
            if (candidate == null || candidate.isNull()) {
                continue;
            }
            if (candidate.isTextual()) {
                return candidate.asText();
            }
            if (candidate.isObject()) {
                for (String nestedKey : TEXT_KEYS) {
                    JsonNode nested = candidate.get(nestedKey);
                    if (nested != null && nested.isTextual()) {
                        return nested.asText();
                    }
                }
            }
        }
        return null;
    }
}
