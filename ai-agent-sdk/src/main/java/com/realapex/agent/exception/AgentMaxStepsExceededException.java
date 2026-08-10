package com.realapex.agent.exception;

import com.realapex.agent.execution.AgentResult;

/**
 * Agent 达到最大步数限制异常。
 * <p>当 ReAct 循环在 {@code maxSteps} 内未产出最终结果时抛出，
 * 携带已执行的中间结果供上层审计与降级处理。</p>
 */
public class AgentMaxStepsExceededException extends RuntimeException {

    private final AgentResult partialResult;

    /**
     * @param maxSteps      配置的最大步数
     * @param partialResult 截止异常发生时的部分执行结果
     */
    public AgentMaxStepsExceededException(int maxSteps, AgentResult partialResult) {
        super("Agent 达到最大步数限制 (" + maxSteps + ")，未能在限制步数内完成最终回答");
        this.partialResult = partialResult;
    }

    /**
     * 获取部分执行结果（用于上层审计与降级处理）。
     *
     * @return 截止异常抛出时的部分执行结果
     */
    public AgentResult getPartialResult() {
        return partialResult;
    }
}
