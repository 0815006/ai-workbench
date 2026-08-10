package com.realapex.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-agent-sdk Spring Boot 配置属性绑定。
 * <p>在 {@code application.yml} 中以 {@code ai.agent} 为前缀配置。</p>
 *
 * <pre>
 * ai:
 *   agent:
 *     max-steps: 10
 *     max-context-tokens: 8000
 *     model: deepseek-chat
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "ai.agent")
public class AgentProperties {

    /** 最大 ReAct 循环步数（防止无限循环），默认 10 */
    private int maxSteps = 10;

    /** 上下文最大 Token 预算（触发裁剪阈值），默认 8000 */
    private int maxContextTokens = 8000;

    /** 默认模型（留空使用 ai-client-sdk 配置的模型） */
    private String model;
}
