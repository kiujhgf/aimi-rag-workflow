package io.github.aimi.rag.workflow.mineru.resolver;

/**
 * MinerU 响应解析异常。
 */
public class MineruParseException extends RuntimeException {

    public MineruParseException(String message) {
        super(message);
    }

    public MineruParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
