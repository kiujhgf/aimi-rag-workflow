package io.github.aimi.rag.workflow.mineru.client;

/**
 * MinerU HTTP 请求异常：非 2xx 响应、IO 错误、超时等。
 */
public class MineruClientException extends RuntimeException {

    public MineruClientException(String message) {
        super(message);
    }

    public MineruClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
