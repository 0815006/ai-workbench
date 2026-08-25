package com.realapex.agent.execution;

import com.realapex.agent.event.AgentEventListener;
import com.realapex.agent.event.AgentStreamListener;
import com.realapex.client.model.Message;
import com.realapex.client.model.ToolCall;
import com.realapex.tool.contract.AgentTool;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 挂起状态快照——HITL（Human-in-the-Loop）中断时的完整上下文。
 * <p>当 LLM 拟调用需要人工审批的高危工具时，{@link AgentRunner} 中断 ReAct 循环，
 * 将当前执行上下文（消息历史、待审批工具调用、累计统计）封装为不可变快照，
 * 供外部系统持久化（Redis/DB）后等待人工审批。</p>
 *
 * <h3>恢复流程</h3>
 * <ol>
 *   <li>AgentRunner 检测到 requiresApproval 工具 → 抛出 {@link AgentSuspendedException}（携带本快照）</li>
 *   <li>外部系统保存 {@code suspendId} 与快照，向人工推送审批请求</li>
 *   <li>人工审批后调用 {@link AgentRunner#resume(String, ApprovalResult)} 恢复执行</li>
 * </ol>
 */
@Data
@Builder
public class AgentState {

    /** 挂起唯一 ID（用于后续 resume 定位） */
    private String suspendId;

    /** 当前 ReAct 循环步数 */
    private int step;

    /** 最大步数限制 */
    private int maxSteps;

    /** 消息历史快照（含 system/user/assistant/tool 全部消息） */
    private List<Message> messages;

    /** 待审批的工具调用列表（LLM 发起但尚未执行） */
    private List<ToolCall> pendingToolCalls;

    /** 累计 prompt tokens */
    private int totalPromptTokens;

    /** 累计 completion tokens */
    private int totalCompletionTokens;

    /** 累计总 tokens */
    private int totalTokens;

    /** 已完成的步骤结果（用于恢复后继续累积） */
    private List<AgentStepResult> stepResults;

    /** 工具列表（恢复时重建 ToolRegistry 用） */
    private List<AgentTool<?, ?>> tools;

    /** 生命周期事件监听器（恢复时继续回调） */
    private AgentEventListener listener;

    /** 流式事件监听器（恢复时继续推送） */
    private AgentStreamListener streamListener;

    /** 请求中的模型名称 */
    private String model;

    /** 请求中的采样温度 */
    private Double temperature;

    /** 是否启用流式输出 */
    private Boolean stream;

    /** 结构化输出目标类型（可选） */
    private Class<?> outputClass;

    /** 挂起时间戳（毫秒） */
    private long suspendedAt;
}