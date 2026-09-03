package com.realapex.tool.db.config;

import com.realapex.tool.db.dialect.DbDialect;
import com.realapex.tool.db.dialect.DbDialectFactory;
import lombok.Builder;
import lombok.Data;

import javax.sql.DataSource;

/**
 * ai-tool-db 工具包核心配置。
 * <p>统一管理连接三要素/方言/截断/超时/行数卡口/连接池参数，通过
 * {@link DbToolFactory} 一键创建 5 个数据库原子工具时传入。</p>
 *
 * <h3>双模式接入</h3>
 * <ul>
 *   <li><b>模式 A（外部注入，推荐）</b>：{@code dataSource} 由应用层自建（HikariCP/Druid/任意实现），
 *       生命周期归应用管理，连接池参数不生效</li>
 *   <li><b>模式 B（工具包托管）</b>：仅提供 {@code jdbcUrl}/{@code username}/{@code password} 三要素，
 *       SDK 内置 HikariCP 兜底构建 Agent 专用小连接池，TTL 超时自动销毁</li>
 * </ul>
 *
 * <h3>Agent 专用连接池默认值</h3>
 * <ul>
 *   <li>{@code maximumPoolSize=5}：Agent 少并发调用工具，防打爆数据库连接数</li>
 *   <li>{@code minimumIdle=1}：保持极低空闲，节省数据库资源</li>
 *   <li>{@code connectionTimeoutMs=5000}：获取连接超 5s 快速失败，触发 Agent 自愈/重试</li>
 *   <li>{@code idleTimeoutMs=60000}：空闲 1 分钟回收（Agent 思考/停顿可能长达数分钟）</li>
 *   <li>{@code maxLifetimeMs=30min}：避免防火墙/网关断开长连接</li>
 *   <li>{@code keepaliveTimeMs=0}：TDSQL 建议 30s 主动保活</li>
 * </ul>
 */
@Data
@Builder
public class DbToolConfig {

    /** 默认返回结果截断上限：20,000 字符（防 Token 爆表） */
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 20_000;

    /** 默认语句级查询超时：10 秒（超时强杀连接） */
    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 10;

    /** 默认只读查询最大返回行数：100（强制 LIMIT，防全表扫描） */
    public static final int DEFAULT_MAX_ROWS = 100;

    /** 默认写操作影响行数上限卡口：500（超出阻断） */
    public static final int DEFAULT_MAX_AFFECTED_ROWS = 500;

    /** 默认连接池最大连接数：5（Agent 专用小池子） */
    public static final int DEFAULT_MAXIMUM_POOL_SIZE = 5;

    /** 默认连接池最小空闲连接数：1 */
    public static final int DEFAULT_MINIMUM_IDLE = 1;

    /** 默认获取连接超时：5 秒 */
    public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 5_000;

    /** 默认空闲回收超时：60 秒 */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 60_000;

    /** 默认连接最大生命周期：30 分钟 */
    public static final long DEFAULT_MAX_LIFETIME_MS = 30 * 60_000L;

    /** 默认 keepalive 时间：0（TDSQL 建议 30_000） */
    public static final long DEFAULT_KEEPALIVE_TIME_MS = 0;

    /** 默认连接探活 SQL：SELECT 1 */
    public static final String DEFAULT_CONNECTION_TEST_QUERY = "SELECT 1";

    /** 默认托管连接池空闲驱逐 TTL：30 分钟未访问自动关闭销毁 */
    public static final long DEFAULT_IDLE_TTL_MINUTES = 30;

    /** 模式 A：应用层注入自建 DataSource（HikariCP/Druid/任意实现），生命周期不归本工具包管理 */
    private DataSource dataSource;

    /** 模式 B：JDBC URL（如 jdbc:mysql://localhost:3306/demo） */
    private String jdbcUrl;

    /** 模式 B：用户名 */
    private String username;

    /** 模式 B：密码 */
    private String password;

    /** 数据库方言，自动探测或显式指定（mysql / tdsql / gaussdb） */
    private DbDialect dialect;

    /** 返回结果截断上限（字符），默认 20,000 */
    @Builder.Default
    private int maxOutputChars = DEFAULT_MAX_OUTPUT_CHARS;

    /** 语句级查询超时（秒），默认 10 秒 */
    @Builder.Default
    private int queryTimeoutSeconds = DEFAULT_QUERY_TIMEOUT_SECONDS;

    /** 只读查询最大返回行数（强制 LIMIT），默认 100 */
    @Builder.Default
    private int maxRows = DEFAULT_MAX_ROWS;

    /** 写操作影响行数上限卡口，默认 500 */
    @Builder.Default
    private int maxAffectedRows = DEFAULT_MAX_AFFECTED_ROWS;

    /** —— 连接池参数（仅模式 B 生效，Agent 专用小池子） —— */

    /** 连接池最大连接数，默认 5 */
    @Builder.Default
    private int maximumPoolSize = DEFAULT_MAXIMUM_POOL_SIZE;

    /** 连接池最小空闲连接数，默认 1 */
    @Builder.Default
    private int minimumIdle = DEFAULT_MINIMUM_IDLE;

    /** 获取连接超时（毫秒），默认 5 秒 */
    @Builder.Default
    private long connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;

    /** 空闲回收超时（毫秒），默认 60 秒 */
    @Builder.Default
    private long idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS;

    /** 连接最大生命周期（毫秒），默认 30 分钟 */
    @Builder.Default
    private long maxLifetimeMs = DEFAULT_MAX_LIFETIME_MS;

    /** keepalive 时间（毫秒），默认 0（TDSQL 建议 30_000） */
    @Builder.Default
    private long keepaliveTimeMs = DEFAULT_KEEPALIVE_TIME_MS;

    /** 连接探活 SQL，默认 SELECT 1 */
    @Builder.Default
    private String connectionTestQuery = DEFAULT_CONNECTION_TEST_QUERY;

    /** 托管连接池空闲驱逐 TTL（分钟），默认 30 分钟未访问自动关闭销毁 */
    @Builder.Default
    private long idleTtlMinutes = DEFAULT_IDLE_TTL_MINUTES;

    /**
     * 获取生效方言：显式配置优先，否则按 JDBC URL 自动探测，兜底 MySQL。
     *
     * @return 方言实例（永不返回 null）
     */
    public DbDialect effectiveDialect() {
        if (dialect != null) {
            return dialect;
        }
        return DbDialectFactory.detect(jdbcUrl);
    }

    /**
     * 获取生效数据源：模式 A 外部注入优先，模式 B 由连接管理器托管构建。
     *
     * @return 数据源（可能为 null，此时需通过 DbConnectionManager 解析）
     */
    public DataSource effectiveDataSource() {
        return dataSource;
    }
}