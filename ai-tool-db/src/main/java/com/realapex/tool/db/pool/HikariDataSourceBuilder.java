package com.realapex.tool.db.pool;

import com.realapex.tool.db.config.DbToolConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * HikariCP 连接池构建器——Agent 专用小池子参数调优。
 * <p>针对 Agent「思考长、慢 SQL 多、串行查库」特性，参数偏向「小池子、快回收、强超时」：</p>
 * <ul>
 *   <li>{@code maximumPoolSize=5~10}：少并发调用，防打爆数据库连接数</li>
 *   <li>{@code minimumIdle=1~2}：极低空闲，节省数据库资源</li>
 *   <li>{@code connectionTimeout=5s}：获取连接快速失败，触发 Agent 自愈/重试</li>
 *   <li>{@code idleTimeout=60s}：空闲 1 分钟回收</li>
 *   <li>{@code maxLifetime=10~30min}：避免防火墙/网关断开长连接（GaussDB/TDSQL 云上关键）</li>
 *   <li>{@code keepaliveTime=30s}（TDSQL 建议）：TProxy 网关空闲断开敏感，主动保活</li>
 * </ul>
 */
@Slf4j
public final class HikariDataSourceBuilder {

    private HikariDataSourceBuilder() {
    }

    /**
     * 按 {@link DbToolConfig} 构建 HikariCP 连接池（模式 B 托管）。
     *
     * @param config 数据库工具配置（三要素 + 连接池参数）
     * @return HikariDataSource 实例
     * @throws IllegalArgumentException jdbcUrl 为空时抛出
     */
    public static HikariDataSource build(DbToolConfig config) {
        if (config.getJdbcUrl() == null || config.getJdbcUrl().isBlank()) {
            throw new IllegalArgumentException("模式 B 托管连接池必须提供 jdbcUrl");
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setPoolName("ai-tool-db-" + Integer.toHexString(config.getJdbcUrl().hashCode()));

        // Agent 专用小池子参数
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
        hikariConfig.setKeepaliveTime(config.getKeepaliveTimeMs());
        hikariConfig.setConnectionTestQuery(config.getConnectionTestQuery());

        // 防泄露：连接归还时自动回滚未提交事务（GaussDB 尤其关键，防 current transaction is aborted）
        hikariConfig.setAutoCommit(true);
        hikariConfig.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

        log.info("构建 Agent 专用连接池: url={}, maxPool={}, minIdle={}, connTimeout={}ms, "
                        + "idleTimeout={}ms, maxLifetime={}ms, keepalive={}ms",
                config.getJdbcUrl(), config.getMaximumPoolSize(), config.getMinimumIdle(),
                config.getConnectionTimeoutMs(), config.getIdleTimeoutMs(),
                config.getMaxLifetimeMs(), config.getKeepaliveTimeMs());

        return new HikariDataSource(hikariConfig);
    }
}