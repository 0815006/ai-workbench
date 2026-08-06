package com.realapex.client.agent;

import com.realapex.client.client.AiClient;

import java.util.List;

/**
 * [二期] 自主多步智能体执行器。
 * <p>基于 OpenAI Function Calling 机制，驱动大模型在 While 循环中
 * 自主决策、调用工具，直到任务完成或达到最大步数限制。</p>
 *
 * <h3>计划功能</h3>
 * <ul>
 *   <li>自动管理 messages 上下文（包含 tool role 消息）</li>
 *   <li>解析 function_call 并调度对应 Tool 执行</li>
 *   <li>可配置最大迭代步数防止无限循环</li>
 *   <li>支持终止条件判断</li>
 * </ul>
 */
public class AgentExecutor {

    private final AiClient client;
    private final List<Tool> tools;
    private final int maxSteps;

    /**
     * @param client   AiClient 实例
     * @param tools    可供调用的工具列表
     * @param maxSteps 最大迭代步数
     */
    public AgentExecutor(AiClient client, List<Tool> tools, int maxSteps) {
        this.client = client;
        this.tools = tools;
        this.maxSteps = maxSteps;
    }

    // TODO: 二期实现完整的 Agent While 循环 + Function Calling 机制
}
