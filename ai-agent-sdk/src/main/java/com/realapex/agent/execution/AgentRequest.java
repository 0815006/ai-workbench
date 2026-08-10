package com.realapex.agent.execution;

import com.realapex.agent.event.AgentEventListener;
import com.realapex.tool.contract.AgentTool;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行请求——单次 {@link AgentRunner#run(AgentRequest)} 的全部入参。
 * <p>采用 Builder 模式，与 {@code AiRequest} 类似的使用体验。</p>
 *
 * <h3>最简用法</h3>
 * <pre>{@code
 * AgentResult result = agentRunner.run(
 *     AgentRequest.builder()
 *         .systemPrompt("你是 MySQL 专家")
 *         .userPrompt("分析慢 SQL: " + sql)
 *         .tools(List.of(explainTool))
 *         .maxSteps(5)
 *         .build());
 * }</pre>
 */
@Data
@Builder
public class AgentRequest {

    /** 系统提示词（注入为 system role message） */
    private String systemPrompt;

    /** 用户输入（注入为 user role message） */
    private String userPrompt;

    /** 附加上下文消息（插在 system 与 user 之间） */
    @Builder.Default
    private List<com.realapex.client.model.Message> messages = new ArrayList<>();

    /** 可用工具列表 */
    @Builder.Default
    private List<AgentTool<?, ?>> tools = new ArrayList<>();

    /** 最大 ReAct 循环步数（防止无限循环），默认 10 */
    @Builder.Default
    private int maxSteps = 10;

    /** 生命周期事件监听器 */
    private AgentEventListener listener;

    /** 模型名称（留空使用 AiClient 默认模型） */
    private String model;

    /** 采样温度，0-2 */
    private Double temperature;

    /** 上下文最大 Token 预算（用于触发裁剪），默认 8000 */
    @Builder.Default
    private int maxContextTokens = 8000;

    /** 结构化输出的目标类型（可选，用于 generateObject 模式） */
    private Class<?> outputClass;
}
