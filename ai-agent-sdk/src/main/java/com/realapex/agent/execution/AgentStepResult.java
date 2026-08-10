package com.realapex.agent.execution;

import com.realapex.client.model.AiResponse;
import com.realapex.client.model.ToolCall;
import com.realapex.client.model.Usage;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单步 ReAct 循环结果。
 * <p>包含本步的全部上下文信息，供 {@code AgentEventListener.onStepFinish()} 使用。</p>
 */
@Data
@Builder
public class AgentStepResult {

    /** 当前步数（从 1 开始） */
    private int stepNumber;

    /** LLM 原始返回 */
    private AiResponse llmResponse;

    /** 本步执行的工具调用列表（LLM 发起的） */
    private List<ToolCall> toolCalls;

    /** 工具调用 ID → 执行结果的映射 */
    private Map<String, Object> toolResults;

    /** 本步 Token 消耗 */
    private Usage usage;

    /** 本步耗时（毫秒） */
    private long durationMs;

    /**
     * 本步是否有工具调用。
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 本步工具调用数。
     */
    public int toolCallCount() {
        return toolCalls != null ? toolCalls.size() : 0;
    }
}
