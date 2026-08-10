package com.realapex.client.client;

import com.realapex.client.model.Usage;

/**
 * 流式响应监听器——SDK 与上层框架的解耦桥梁。
 * <p>业务方可在回调中自由对接 SseEmitter、WebSocket、日志等任意消费方式。
 * 典型用法见 {@link AiClient#streamText}。</p>
 *
 * <p>所有新增回调方法均有默认空实现，保持向后兼容。</p>
 */
public interface StreamListener {

    /**
     * 接收到一个文本块（delta content）。
     *
     * @param chunk 增量文本内容
     */
    void onChunk(String chunk);

    /**
     * 接收到工具调用增量片段。
     * <p>当大模型在流式输出中发起 Function Calling 时触发。
     * 单次工具调用的 arguments 可能分多帧到达，业务方可使用 {@code StreamToolCallBuffer} 累积拼接。</p>
     *
     * @param callId         工具调用唯一 ID
     * @param name           工具名称（首次出现时非空，后续帧可能为 null）
     * @param argumentsDelta 参数 JSON 增量片段
     */
    default void onToolCallChunk(String callId, String name, String argumentsDelta) {
        // 默认空实现，保持向后兼容
    }

    /**
     * 接收到 Token 用量统计（通常为流式最后一帧）。
     *
     * @param usage Token 用量
     */
    default void onUsage(Usage usage) {
        // 默认空实现，保持向后兼容
    }

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
