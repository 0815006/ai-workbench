package com.realapex.agent.config;

import com.realapex.agent.execution.AgentRunner;
import com.realapex.tool.schema.SchemaGenerator;
import com.realapex.agent.tool.ToolRegistry;
import com.realapex.client.client.AiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ai-agent-sdk Spring Boot 自动配置。
 * <p>当 classpath 中存在 Spring Boot、已配置 AiClient Bean 时自动生效。
 * 纯 Java 项目（无 Spring）可直接通过构造器手动创建各组件。</p>
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li>{@link SchemaGenerator} — JSON Schema 生成器</li>
 *   <li>{@link ToolRegistry} — 工具注册表</li>
 *   <li>{@link ToolBeanPostProcessor} — @Tool 注解扫描器</li>
 *   <li>{@link AgentRunner} — ReAct 循环驱动器</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(AiClient.class)
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    /**
     * SchemaGenerator Bean。
     */
    @Bean
    public SchemaGenerator schemaGenerator() {
        return new SchemaGenerator();
    }

    /**
     * ToolRegistry Bean。
     */
    @Bean
    public ToolRegistry toolRegistry(SchemaGenerator schemaGenerator) {
        return new ToolRegistry(schemaGenerator);
    }

    /**
     * ToolBeanPostProcessor——自动扫描 @Tool 注解。
     */
    @Bean
    public ToolBeanPostProcessor toolBeanPostProcessor(ToolRegistry toolRegistry) {
        return new ToolBeanPostProcessor(toolRegistry);
    }

    /**
     * AgentRunner Bean——ReAct 循环引擎。
     * <p>依赖 AiClient（由 ai-client-sdk 自动配置提供）。</p>
     */
    @Bean
    @ConditionalOnBean(AiClient.class)
    public AgentRunner agentRunner(AiClient aiClient, SchemaGenerator schemaGenerator) {
        return new AgentRunner(aiClient, schemaGenerator);
    }
}
