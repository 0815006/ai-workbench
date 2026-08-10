package com.realapex.agent.execution;

import com.realapex.client.model.Usage;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Agent 执行最终结果。
 * <p>包含最终文本输出、执行统计元数据和每步详细轨迹。</p>
 */
@Data
@Builder
public class AgentResult {

    /** 最终文本输出（Agent 完成时的自然语言回复） */
    private String finalText;

    /** 结构化输出对象（当使用了 outputClass 参数时非空） */
    private Object structuredOutput;

    /** 总执行步数 */
    private int totalSteps;

    /** 累计 Token 消耗 */
    private Usage totalUsage;

    /** 总耗时（毫秒） */
    private long totalDurationMs;

    /** 每步详细结果（按执行顺序排列） */
    @Builder.Default
    private List<AgentStepResult> stepResults = Collections.emptyList();

    /**
     * 获取总 Token 数。
     *
     * @return 总 Token 数，无 usage 信息时返回 0
     */
    public int getTotalTokens() {
        return totalUsage != null ? totalUsage.getTotalTokens() : 0;
    }

    /**
     * 是否因达到 maxSteps 而提前终止。
     */
    @Builder.Default
    private boolean maxStepsExceeded = false;
}
