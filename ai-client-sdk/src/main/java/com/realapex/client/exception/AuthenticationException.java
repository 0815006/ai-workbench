package com.realapex.client.exception;

/**
 * 认证异常（HTTP 401 Unauthorized / 402 Payment Required）。
 * <p>表示 API Key 无效、过期或余额不足。</p>
 */
public class AuthenticationException extends AiClientException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
