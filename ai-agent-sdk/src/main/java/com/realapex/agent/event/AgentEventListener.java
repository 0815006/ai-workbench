package com.realapex.agent.event;

import com.realapex.agent.execution.AgentResult;
import com.realapex.agent.execution.AgentStepResult;

/**
 * Agent 生命周期事件监听器。
 * <p>所有回调方法均有默认空实现，业务方可选择性覆盖所需事件。
 * 典型应用场景：SSE 前端推播、执行轨迹持久化（落库/Redis）、日志审计。</p>
 *
 * <h3>推荐用法</h3>
 * <ul>
 *   <li><b>SSE 推播</b>：覆盖 {@link #onChunk} 做前端打字机效果</li>
 *   <li><b>轨迹持久化</b>：覆盖 {@link #onStepFinish} 将每步 Token 消耗和工具调用结果写入数据库</li>
 *   <li><b>运维告警</b>：覆盖 {@link #onToolStart}/{@link #onToolEnd} 监控工具调用耗时</li>
 * </ul>
 */
public interface AgentEventListener {

    /**
     * 每一步 ReAct 循环开始。
     *
     * @param step 当前步数（从 1 开始）
     */
    default void onStepStart(int step) {
    }

    /**
     * 每一步 ReAct 循环结束，携带本步完整上下文。
     * <p><b>推荐在此回调中做轨迹持久化（落库/Redis）与前端 SSE 增量推播。</b></p>
     *
     * @param stepResult 本步完整结果（步数、LLM 原始返回、Tool 调用与结果、Token 消耗）
     */
    default void onStepFinish(AgentStepResult stepResult) {
    }

    /**
     * 单个工具调用开始。
     *
     * @param toolName 工具名称
     * @param args     工具参数（JSON 字符串）
     */
    default void onToolStart(String toolName, String args) {
    }

    /**
     * 单个工具调用结束。
     *
     * @param toolName 工具名称
     * @param result   工具返回结果
     */
    default void onToolEnd(String toolName, Object result) {
    }

    /**
     * 打字机流式增量文本推送（仅流式模式生效）。
     *
     * @param textChunk 增量文本
     */
    default void onChunk(String textChunk) {
    }

    /**
     * Agent 整体执行完成。
     *
     * @param result 最终结果（含 finalText、totalSteps、totalTokens、总耗时）
     */
    default void onComplete(AgentResult result) {
    }
}
