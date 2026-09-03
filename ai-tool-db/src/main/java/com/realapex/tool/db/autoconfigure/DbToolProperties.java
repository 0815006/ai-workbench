package com.realapex.tool.db.autoconfigure;

import com.realapex.tool.db.config.DbToolConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-tool-db Spring Boot 配置属性（前缀 {@code realapex.tool.db}）。
 *
 * <p>示例 application.yml：</p>
 * <pre>{@code
 * realapex:
 *   tool:
 *     db:
 *       jdbc-url: jdbc:mysql://localhost:3306/mydb
 *       username: root
 *       password: ${DB_PASSWORD}
 *       dialect: mysql          # mysql / tdsql / gaussdb，留空自动探测
 *       maximum-pool-size: 5
 *       minimum-idle: 1
 *       connection-timeout-ms: 5000
 *       idle-timeout-ms: 60000
 *       max-lifetime-ms: 1800000
 *       keepalive-time-ms: 0
 *       idle-ttl-minutes: 30
 *       max-output-chars: 20000
 *       query-timeout-seconds: 10
 *       max-rows: 100
 *       max-affected-rows: 500
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "realapex.tool.db")
public class DbToolProperties {

    /** 模式 B：JDBC URL（如 jdbc:mysql://localhost:3306/mydb） */
    private String jdbcUrl;

    /** 模式 B：用户名 */
    private String username;

    /** 模式 B：密码 */
    private String password;

    /** 方言名称（mysql / tdsql / gaussdb），留空自动探测 */
    private String dialect;

    /** 返回结果截断上限（字符），默认 20,000 */
    private int maxOutputChars = DbToolConfig.DEFAULT_MAX_OUTPUT_CHARS;

    /** 语句级查询超时（秒），默认 10 秒 */
    private int queryTimeoutSeconds = DbToolConfig.DEFAULT_QUERY_TIMEOUT_SECONDS;

    /** 只读查询最大返回行数，默认 100 */
    private int maxRows = DbToolConfig.DEFAULT_MAX_ROWS;

    /** 写操作影响行数上限卡口，默认 500 */
    private int maxAffectedRows = DbToolConfig.DEFAULT_MAX_AFFECTED_ROWS;

    /** 连接池最大连接数，默认 5 */
    private int maximumPoolSize = DbToolConfig.DEFAULT_MAXIMUM_POOL_SIZE;

    /** 连接池最小空闲连接数，默认 1 */
    private int minimumIdle = DbToolConfig.DEFAULT_MINIMUM_IDLE;

    /** 获取连接超时（毫秒），默认 5 秒 */
    private long connectionTimeoutMs = DbToolConfig.DEFAULT_CONNECTION_TIMEOUT_MS;

    /** 空闲回收超时（毫秒），默认 60 秒 */
    private long idleTimeoutMs = DbToolConfig.DEFAULT_IDLE_TIMEOUT_MS;

    /** 连接最大生命周期（毫秒），默认 30 分钟 */
    private long maxLifetimeMs = DbToolConfig.DEFAULT_MAX_LIFETIME_MS;

    /** keepalive 时间（毫秒），默认 0（TDSQL 建议 30_000） */
    private long keepaliveTimeMs = DbToolConfig.DEFAULT_KEEPALIVE_TIME_MS;

    /** 连接探活 SQL，默认 SELECT 1 */
    private String connectionTestQuery = DbToolConfig.DEFAULT_CONNECTION_TEST_QUERY;

    /** 托管连接池空闲驱逐 TTL（分钟），默认 30 */
    private long idleTtlMinutes = DbToolConfig.DEFAULT_IDLE_TTL_MINUTES;

    /**
     * 转换为领域配置对象 {@link DbToolConfig}。
     *
     * @return DbToolConfig 实例
     */
    public DbToolConfig toConfig() {
        return DbToolConfig.builder()
                .jdbcUrl(jdbcUrl)
                .username(username)
                .password(password)
                .dialect(dialect == null || dialect.isBlank() ? null
                        : com.realapex.tool.db.dialect.DbDialectFactory.create(dialect))
                .maxOutputChars(maxOutputChars)
                .queryTimeoutSeconds(queryTimeoutSeconds)
                .maxRows(maxRows)
                .maxAffectedRows(maxAffectedRows)
                .maximumPoolSize(maximumPoolSize)
                .minimumIdle(minimumIdle)
                .connectionTimeoutMs(connectionTimeoutMs)
                .idleTimeoutMs(idleTimeoutMs)
                .maxLifetimeMs(maxLifetimeMs)
                .keepaliveTimeMs(keepaliveTimeMs)
                .connectionTestQuery(connectionTestQuery)
                .idleTtlMinutes(idleTtlMinutes)
                .build();
    }
}