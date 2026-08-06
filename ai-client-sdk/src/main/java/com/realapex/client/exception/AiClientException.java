package com.realapex.client.exception;

/**
 * ai-client-sdk 根异常。
 * <p>所有 SDK 自定义异常均继承此类。即使是非受检异常，调用方也可通过
 * {@code catch (AiClientException e)} 统一兜底处理所有 SDK 错误。</p>
 */
public class AiClientException extends RuntimeException {

    /**
     * @param message 可操作的错误描述（如 "API Key 失效，请检查配置"）
     */
    public AiClientException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   底层异常
     */
    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
