package com.realapex.agent.execution;

/**
 * 人工审批结果——HITL 恢复执行的输入参数。
 * <p>外部系统在人工审批完成后，将审批结论封装为本对象，
 * 调用 {@code AgentRunner.resume(suspendId, ApprovalResult)} 恢复 Agent 执行。</p>
 *
 * <h3>审批语义</h3>
 * <ul>
 *   <li><b>批准</b>：{@code approved=true}，Agent 继续执行待审批工具</li>
 *   <li><b>拒绝</b>：{@code approved=false}，Agent 将拒绝结果回传 LLM，驱动 LLM 调整策略</li>
 * </ul>
 *
 * @param approved  是否批准执行
 * @param operator  审批人（工号/用户名，用于审计）
 * @param comment   审批意见（可选，回传 LLM 作为修正指引）
 * @param approvedAt 审批时间戳（毫秒）
 */
public record ApprovalResult(boolean approved, String operator, String comment, long approvedAt) {

    /**
     * 快捷工厂：批准。
     *
     * @param operator 审批人
     * @param comment  审批意见
     * @return ApprovalResult
     */
    public static ApprovalResult approve(String operator, String comment) {
        return new ApprovalResult(true, operator, comment, System.currentTimeMillis());
    }

    /**
     * 快捷工厂：拒绝。
     *
     * @param operator 审批人
     * @param comment  拒绝原因
     * @return ApprovalResult
     */
    public static ApprovalResult reject(String operator, String comment) {
        return new ApprovalResult(false, operator, comment, System.currentTimeMillis());
    }
}