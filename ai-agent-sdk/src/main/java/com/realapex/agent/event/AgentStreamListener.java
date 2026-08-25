package com.realapex.agent.event;

/**
 * Agent 流式事件监听器——SSE 中间步骤实时推送的接入点。
 * <p>与 {@link AgentEventListener}（同步生命周期回调）不同，本接口面向
 * <b>实时流式推送</b>场景：将 Agent 的思考增量、工具触发、工具结果、
 * 最终输出统一封装为 {@link AgentStreamEvent} 逐帧推送，可直接对接
 * SseEmitter / WebSocket / 响应式流。</p>
 *
 * <h3>典型用法（SSE 前端推播）</h3>
 * <pre>{@code
 * AgentRequest request = AgentRequest.builder()
 *         .systemPrompt("你是运维专家")
 *         .userPrompt("排查线上故障")
 *         .tools(tools)
 *         .streamListener(event -> {
 *             switch (event) {
 *                 case AgentStreamEvent.ThoughtChunk(var text) ->
 *                         sseEmitter.send(SseEmitter.event().data(text).name("thought"));
 *                 case AgentStreamEvent.ToolCallStart(var id, var name, var args) ->
 *                         sseEmitter.send(SseEmitter.event().data(name).name("tool_start"));
 *                 case AgentStreamEvent.ToolCallResult(var id, var name, var result) ->
 *                         sseEmitter.send(SseEmitter.event().data(result).name("tool_result"));
 *                 case AgentStreamEvent.FinalResult(var text, var output) ->
 *                         sseEmitter.send(SseEmitter.event().data(text).name("final"));
 *             }
 *         })
 *         .build();
 * }</pre>
 *
 * <p>所有回调方法均有默认空实现，业务方可选择性覆盖。</p>
 */
public interface AgentStreamListener {

    /**
     * 接收一个 Agent 流式事件。
     * <p>事件类型见 {@link AgentStreamEvent} 的四个变体。</p>
     *
     * @param event Agent 流式事件
     */
    default void onEvent(AgentStreamEvent event) {
    }

    /**
     * 思考增量推送（等价于 {@code onEvent(new ThoughtChunk(text))} 的快捷方法）。
     *
     * @param text 思考增量文本
     */
    default void onThoughtChunk(String text) {
    }

    /**
     * 工具触发推送（等价于 {@code onEvent(new ToolCallStart(...))} 的快捷方法）。
     *
     * @param callId    工具调用唯一 ID
     * @param name      工具名称
     * @param arguments 参数 JSON 字符串
     */
    default void onToolCallStart(String callId, String name, String arguments) {
    }

    /**
     * 工具结果推送（等价于 {@code onEvent(new ToolCallResult(...))} 的快捷方法）。
     *
     * @param callId 工具调用唯一 ID
     * @param name   工具名称
     * @param result 工具执行结果
     */
    default void onToolCallResult(String callId, String name, Object result) {
    }

    /**
     * 最终结果推送（等价于 {@code onEvent(new FinalResult(...))} 的快捷方法）。
     *
     * @param text             最终文本
     * @param structuredOutput 结构化输出对象（未指定 outputClass 时为 null）
     */
    default void onFinalResult(String text, Object structuredOutput) {
    }
}