package com.realapex.client.exception;

/**
 * 响应解析异常。
 * <p>大模型返回内容无法反序列化为目标 Java 类型时抛出。</p>
 */
public class ParseException extends AiClientException {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
