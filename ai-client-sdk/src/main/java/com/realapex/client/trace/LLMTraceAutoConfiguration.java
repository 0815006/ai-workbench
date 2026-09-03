package com.realapex.client.trace;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * LLM Trace 持久化 Spring Boot 自动配置。
 * <p>当 {@code ai.client.trace.enabled=true} 且应用侧存在 {@link DataSource} 时自动生效：</p>
 * <ul>
 *   <li>装配 {@link AsyncLLMLogStorageService}（复用应用侧 DataSource）</li>
 *   <li>装配 {@link LLMInvokeTraceInterceptor}</li>
 *   <li>若开启 {@code auto-ddl}，触发 {@link LLMTraceTableAutoInitializer} 兜底建表</li>
 * </ul>
 * <p>纯 Java 项目（无 Spring/无自动配置）可手动创建这些组件。</p>
 */
@Configuration
@EnableConfigurationProperties(LLMTraceProperties.class)
@ConditionalOnProperty(name = "ai.client.trace.enabled", havingValue = "true")
public class LLMTraceAutoConfiguration {

    /**
     * 装配异步落盘服务，优先复用应用侧 DataSource。
     *
     * @param dataSourceProvider 应用侧数据源（可能不存在）
     * @param properties         Trace 配置
     * @return 落盘服务，无可用数据源时返回 null（此时由调用方决定是否启用）
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AsyncLLMLogStorageService asyncLlmLogStorageService(
            ObjectProvider<DataSource> dataSourceProvider,
            LLMTraceProperties properties) {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            if (properties.getJdbc() != null && properties.getJdbc().isEnabled()) {
                // JDBC 直连兜底：基于配置自建 HikariCP
                ds = buildJdbcOnlyDataSource(properties.getJdbc());
            }
        }
        if (ds == null) {
            // 无数据源时无法落盘，仍返回 null，由拦截器检测后自动降级（日志告警而非抛错）
            return null;
        }
        return new AsyncLLMLogStorageService(ds, properties.getTableName(), properties.getAsyncPoolSize());
    }

    /**
     * 装配 Trace 拦截器。
     *
     * @param storageService ObjectProvider 防止无数据源时返回 null
     * @param properties     Trace 配置
     * @return 拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LLMInvokeTraceInterceptor llmInvokeTraceInterceptor(
            ObjectProvider<AsyncLLMLogStorageService> storageService,
            LLMTraceProperties properties) {
        AsyncLLMLogStorageService storage = storageService.getIfAvailable();
        return new LLMInvokeTraceInterceptor(storage, properties);
    }

    /**
     * Auto-DDL 兜底建表（可选，默认关闭）。
     *
     * @param dataSourceProvider 应用侧数据源
     * @param properties         Trace 配置
     */
    @Bean
    @ConditionalOnProperty(name = "ai.client.trace.auto-ddl", havingValue = "true")
    public LLMTraceTableAutoInitializer llmTraceTableAutoInitializer(
            ObjectProvider<DataSource> dataSourceProvider,
            LLMTraceProperties properties) {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null && properties.getJdbc() != null && properties.getJdbc().isEnabled()) {
            ds = buildJdbcOnlyDataSource(properties.getJdbc());
        }
        if (ds == null) {
            return null;
        }
        LLMTraceTableAutoInitializer initializer =
                new LLMTraceTableAutoInitializer(ds, properties.getTableName());
        initializer.initializeIfNeeded();
        return initializer;
    }

    /**
     * 基于配置自建 HikariCP 数据源（JDBC-Only 兜底，避免强依赖应用 DataSource）。
     *
     * @param jdbc JDBC 直连配置
     * @return HikariDataSource，包存在时创建
     */
    private DataSource buildJdbcOnlyDataSource(LLMTraceProperties.Jdbc jdbc) {
        try {
            Class.forName("com.zaxxer.hikari.HikariDataSource");
            com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
            ds.setJdbcUrl(jdbc.getJdbcUrl());
            ds.setUsername(jdbc.getUsername());
            ds.setPassword(jdbc.getPassword());
            ds.setMaximumPoolSize(Math.max(1, jdbc.getMaxPoolSize()));
            return ds;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}