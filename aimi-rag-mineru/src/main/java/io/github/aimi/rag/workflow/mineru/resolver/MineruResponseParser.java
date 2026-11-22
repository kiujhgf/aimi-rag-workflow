package io.github.aimi.rag.workflow.mineru.resolver;

import io.github.aimi.rag.workflow.mineru.model.SourceFile;

import java.util.List;

/**
 * MinerU 响应解析器：把 MinerU 原始 JSON 响应体转换为 {@code List<String>} 解析文本。
 *
 * <p>接口抽象便于应对不同部署的响应字段差异。默认实现为 {@link MarkdownResponseParser}。</p>
 */
public interface MineruResponseParser {

    /**
     * 解析 MinerU 响应体。
     *
     * @param responseBody 原始 JSON 响应字符串
     * @param sources      原始输入文件列表（用于对齐顺序、匹配文件名）
     * @return 解析出的文本列表，顺序与 {@code sources} 对齐
     * @throws MineruParseException 响应为空、JSON 格式错误、无法提取产物
     */
    List<String> parse(String responseBody, List<SourceFile> sources) throws MineruParseException;
}
