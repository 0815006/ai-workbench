package com.realapex.tool.doc.config;

import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.security.ToolSecurityInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * ai-tool-doc Spring Boot 自动配置。
 * <p>当 classpath 中存在 ai-tool-sdk 契约（{@link AgentTool}）时自动生效，
 * 注册文档工具组与领域专属路径拦截器。纯 Java 项目（无 Spring）可直接通过
 * {@link DocToolFactory} 手动创建。</p>
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li>{@link DocToolConfig} — 领域配置（由 {@code realapex.tool.doc.*} 属性绑定）</li>
 *   <li>{@code docTools} — 3 个文档原子工具（read_and_convert_doc / inspect_template_schema / render_document）</li>
 *   <li>{@code documentPathInterceptor} — 领域专属路径拦截器（优先级 5）</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(AgentTool.class)
@EnableConfigurationProperties(DocToolProperties.class)
public class DocToolAutoConfiguration {

    /**
     * 领域配置 Bean。
     *
     * @param properties Spring Boot 配置属性
     * @return DocToolConfig
     */
    @Bean
    @ConditionalOnMissingBean
    public DocToolConfig docToolConfig(DocToolProperties properties) {
        return properties.toConfig();
    }

    /**
     * 文档工具组 Bean（3 个原子工具）。
     *
     * @param config 领域配置
     * @return 文档工具列表
     */
    @Bean
    @ConditionalOnMissingBean
    public List<AgentTool<?, ?>> docTools(DocToolConfig config) {
        return DocToolFactory.createDocTools(config);
    }

    /**
     * 领域专属路径拦截器 Bean（优先级 5，先于通用链执行）。
     *
     * @param config 领域配置
     * @return 路径拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolSecurityInterceptor documentPathInterceptor(DocToolConfig config) {
        return DocToolFactory.createPathInterceptor(config);
    }
}