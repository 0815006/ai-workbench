package com.realapex.tool.db.autoconfigure;

import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.config.DbToolFactory;
import com.realapex.tool.db.pool.DbConnectionManager;
import com.realapex.tool.security.ToolSecurityInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;

/**
 * ai-tool-db Spring Boot 自动配置。
 * <p>当 classpath 中存在 ai-tool-sdk 契约（{@link AgentTool}）时自动生效，
 * 注册数据库工具组与领域专属只读 SQL 拦截器。纯 Java 项目（无 Spring）可直接通过
 * {@link DbToolFactory} 手动创建。</p>
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li>{@link DbToolConfig} — 领域配置（由 {@code realapex.tool.db.*} 属性绑定）</li>
 *   <li>{@code dbTools} — 5 个数据库原子工具（get_db_schema / readonly_query / explain_sql /
 *       fetch_slow_logs / execute_update）</li>
 *   <li>{@code readOnlySqlInterceptor} — 领域专属只读 SQL 拦截器（优先级 5）</li>
 *   <li>{@link DbConnectionManager} — 动态多数据源管理器（模式 B 托管连接池 + TTL 驱逐）</li>
 * </ul>
 *
 * <h3>接入模式</h3>
 * <ul>
 *   <li><b>模式 A（外部注入）</b>：应用已有 {@link DataSource} Bean，自动注入复用</li>
 *   <li><b>模式 B（工具包托管）</b>：配置 {@code realapex.tool.db.jdbc-url} 三要素，
 *       自动构建 Agent 专用小连接池</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(AgentTool.class)
@EnableConfigurationProperties(DbToolProperties.class)
public class DbToolAutoConfiguration {

    /**
     * 领域配置 Bean。
     *
     * @param properties Spring Boot 配置属性
     * @return DbToolConfig
     */
    @Bean
    @ConditionalOnMissingBean
    public DbToolConfig dbToolConfig(DbToolProperties properties) {
        return properties.toConfig();
    }

    /**
     * 数据库工具组 Bean（5 个原子工具）。
     *
     * @param config 领域配置
     * @return 数据库工具列表
     */
    @Bean
    @ConditionalOnMissingBean
    public List<AgentTool<?, ?>> dbTools(DbToolConfig config) {
        return DbToolFactory.createDbTools(config);
    }

    /**
     * 领域专属只读 SQL 拦截器 Bean（优先级 5，先于通用链执行）。
     *
     * @return 只读 SQL 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolSecurityInterceptor readOnlySqlInterceptor() {
        return DbToolFactory.createReadOnlyInterceptor();
    }

    /**
     * 动态多数据源管理器 Bean（模式 B 托管连接池 + TTL 驱逐）。
     * <p>当应用存在外部 {@link DataSource} Bean 时自动注入（模式 A）；\n
     * 否则由配置三要素构建托管连接池（模式 B）。</p>
     *
     * @param dataSource 应用层 DataSource（可为 null，此时走模式 B）
     * @param config     领域配置
     * @return 连接管理器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public DbConnectionManager dbConnectionManager(DataSource dataSource, DbToolConfig config) {
        DbConnectionManager manager = new DbConnectionManager(dataSource, config.getIdleTtlMinutes());
        // 模式 B：配置了三要素时自动注册默认数据源
        if (dataSource == null && config.getJdbcUrl() != null && !config.getJdbcUrl().isBlank()) {
            manager.register("default", config);
            log.info("DbToolAutoConfiguration: 模式 B 自动注册默认托管数据源");
        }
        return manager;
    }
}