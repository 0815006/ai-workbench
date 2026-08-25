package com.realapex.client.stream;

import com.realapex.client.model.Usage;

/**
 * SSE 流式事件统一抽象（Java 21 sealed interface）。
 * <p>将 SSE 流式输出的各类增量数据（文本、工具调用、Token 统计）
 * 统一为类型安全的事件层次结构，调用方可通过 {@code switch} 模式匹配处理。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * switch (event) {
 *     case StreamEvent.TextChunk(var text)       -> System.out.print(text);
 *     case StreamEvent.ToolCallChunk(var id, var name, var args) -> buffer.accept(event);
 *     case StreamEvent.Usage(var usage)          -> log.info("tokens: {}", usage.getTotalTokens());
 *     case StreamEvent.Complete()                -> done = true;
 * }
 * }</pre>
 */
public sealed interface StreamEvent
        permits StreamEvent.TextChunk, StreamEvent.ReasoningChunk, StreamEvent.ToolCallChunk,
                StreamEvent.UsageEvent, StreamEvent.Complete {

    /**
     * 文本增量事件。
     */
    record TextChunk(String content) implements StreamEvent {
    }

    /**
     * 推理/思考链增量事件（DeepSeek-R1、o1 等推理模型专用）。
     * <p>与 {@link TextChunk} 的区别：本事件推送的是模型的思考过程
     * （reasoning_content），而非最终回答文本。前端可将其展示为
     * "思考中" 折叠面板。</p>
     *
     * @param content 推理内容增量
     */
    record ReasoningChunk(String content) implements StreamEvent {
    }

    /**
     * 工具调用增量事件。
     *
     * @param index          工具调用在列表中的序号（用于关联无 callId 的后续分片）
     * @param callId         工具调用唯一 ID（首帧出现，后续帧可能为 null）
     * @param name           工具函数名称（首帧出现，后续帧可能为 null）
     * @param argumentsDelta 参数 JSON 增量片段
     */
    record ToolCallChunk(Integer index, String callId, String name, String argumentsDelta)
            implements StreamEvent {
    }

    /**
     * Token 用量统计事件（通常为最后一帧）。
     */
    record UsageEvent(Usage usage) implements StreamEvent {
    }

    /**
     * 流结束事件。
     */
    record Complete() implements StreamEvent {
    }
}
