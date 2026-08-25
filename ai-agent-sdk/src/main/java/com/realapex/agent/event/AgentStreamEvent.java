package com.realapex.agent.event;

/**
 * Agent 流式事件统一抽象（Java 21 sealed interface）。
 * <p>将 Agent 执行过程中的生命周期增量（思考、工具调用、工具结果、最终输出）
 * 统一为类型安全的事件层次结构，配合 {@link AgentStreamListener} 实现
 * SSE 中间步骤实时推送（打字机 + 中间步骤展示）。</p>
 *
 * <h3>事件变体</h3>
 * <ul>
 *   <li>{@link ThoughtChunk} — 思考增量（LLM 推理内容 / 中间文本）</li>
 *   <li>{@link ToolCallStart} — 工具触发（LLM 发起工具调用）</li>
 *   <li>{@link ToolCallResult} — 工具结果（工具执行完成返回）</li>
 *   <li>{@link FinalResult} — 最终结果（Agent 完成，含最终文本/结构化输出）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * switch (event) {
 *     case AgentStreamEvent.ThoughtChunk(var text) -> sse.send("data: " + text);
 *     case AgentStreamEvent.ToolCallStart(var id, var name, var args) -> sse.send("tool_start: " + name);
 *     case AgentStreamEvent.ToolCallResult(var id, var name, var result) -> sse.send("tool_result: " + result);
 *     case AgentStreamEvent.FinalResult(var text, var output) -> sse.send("final: " + text);
 * }
 * }</pre>
 */
public sealed interface AgentStreamEvent
        permits AgentStreamEvent.ThoughtChunk, AgentStreamEvent.ToolCallStart,
                AgentStreamEvent.ToolCallResult, AgentStreamEvent.FinalResult {

    /**
     * 思考增量事件。
     * <p>对应 LLM 的推理内容（DeepSeek-R1 的 reasoning_content）或
     * Agent 中间过程文本，用于前端展示"思考中"状态。</p>
     *
     * @param content 思考增量文本
     */
    record ThoughtChunk(String content) implements AgentStreamEvent {
    }

    /**
     * 工具触发事件。
     * <p>当 LLM 决定调用某个工具时触发，携带完整参数 JSON。</p>
     *
     * @param callId    工具调用唯一 ID
     * @param name      工具名称
     * @param arguments 参数 JSON 字符串
     */
    record ToolCallStart(String callId, String name, String arguments) implements AgentStreamEvent {
    }

    /**
     * 工具结果事件。
     * <p>工具执行完成后触发，携带执行结果（成功数据或错误信息）。</p>
     *
     * @param callId 工具调用唯一 ID
     * @param name   工具名称
     * @param result 工具执行结果
     */
    record ToolCallResult(String callId, String name, Object result) implements AgentStreamEvent {
    }

    /**
     * 最终结果事件。
     * <p>Agent 完成 ReAct 循环后触发，携带最终文本与可选的结构化输出。</p>
     *
     * @param text             最终文本
     * @param structuredOutput 结构化输出对象（未指定 outputClass 时为 null）
     */
    record FinalResult(String text, Object structuredOutput) implements AgentStreamEvent {
    }
}