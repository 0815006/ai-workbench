package com.realapex.client.client;

/**
 * 流式响应监听器——SDK 与上层框架的解耦桥梁。
 * <p>业务方可在回调中自由对接 SseEmitter、WebSocket、日志等任意消费方式。
 * 典型用法见 {@link AiClient#streamText}。</p>
 */
public interface StreamListener {

    /**
     * 接收到一个文本块（delta content）。
     *
     * @param chunk 增量文本内容
     */
    void onChunk(String chunk);

    /**
     * 流式输出正常结束。
     */
    void onComplete();

    /**
     * 流式输出异常终止。
     *
     * @param throwable 异常信息
     */
    void onError(Throwable throwable);
}
