package com.realapex.agent.exception;

import com.realapex.agent.execution.AgentState;

/**
 * Agent 挂起异常——HITL（Human-in-the-Loop）中断信号。
 * <p>当 LLM 拟调用需要人工审批的高危工具（{@code @Tool(requiresApproval = true)}）时，
 * {@code AgentRunner} 中断 ReAct 循环并抛出本异常，携带完整的 {@link AgentState} 快照。
 * 调用方捕获后应：</p>
 * <ol>
 *   <li>持久化 {@link AgentState}（Redis/DB），记录 {@code suspendId}</li>
 *   <li>向人工推送审批请求（工具名、参数、风险说明）</li>
 *   <li>人工审批后调用 {@code AgentRunner.resume(suspendId, ApprovalResult)} 恢复执行</li>
 * </ol>
 *
 * <h3>与 {@code AgentMaxStepsExceededException} 的区别</h3>
 * <ul>
 *   <li>本异常：<b>可恢复</b>——等待人工审批后继续执行</li>
 *   <li>超步异常：<b>不可恢复</b>——达到步数上限直接终止</li>
 * </ul>
 */
public class AgentSuspendedException extends RuntimeException {

    /** 挂起状态快照（含消息历史、待审批工具调用、累计统计） */
    private final AgentState agentState;

    /**
     * 创建挂起异常。
     *
     * @param agentState 挂起状态快照
     */
    public AgentSuspendedException(AgentState agentState) {
        super("Agent 已挂起，等待人工审批: suspendId=" + agentState.getSuspendId()
                + ", 待审批工具=" + agentState.getPendingToolCalls());
        this.agentState = agentState;
    }

    /**
     * 获取挂起状态快照。
     *
     * @return AgentState 快照
     */
    public AgentState getAgentState() {
        return agentState;
    }

    /**
     * 获取挂起唯一 ID。
     *
     * @return suspendId
     */
    public String getSuspendId() {
        return agentState.getSuspendId();
    }
}