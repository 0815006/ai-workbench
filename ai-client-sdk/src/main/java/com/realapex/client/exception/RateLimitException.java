package com.realapex.client.exception;

/**
 * 请求频率超限异常（HTTP 429）。
 * <p>SDK 内部已自动重试，此异常表示重试耗尽后仍失败。</p>
 */
public class RateLimitException extends AiClientException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
