package com.realapex.client.trace;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 调用日志与 Trace 持久化配置属性（前缀 {@code ai.client.trace}）。
 *
 * <pre>
 * ai:
 *   client:
 *     trace:
 *       enabled: true              # 开启通用日志记录
 *       auto-ddl: true             # 启动时若没有 sys_llm_invoke_log 表，自动建表
 *       max-payload-length: 50000  # 单条日志 request/response JSON 最大字符长度（超长截断）
 *       table-name: sys_llm_invoke_log
 *       # 可选：JDBC-Only 兜底数据源（未复用应用 DataSource 时配置）
 *       jdbc-only:
 *         jdbc-url: jdbc:postgresql://localhost:5432/app
 *         username: postgres
 *         password: postgres
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "ai.client.trace")
public class LLMTraceProperties {

    /** 是否开启通用日志记录（默认关闭，避免无痕污染） */
    private boolean enabled = false;

    /** 启动时若未建表，自动执行 DDL 建表（兜底，应用侧无 Flyway 时使用） */
    private boolean autoDdl = false;

    /** 单条日志 request_payload / response_payload 最大字符长度（0 表示不截断） */
    private long maxPayloadLength = 100000L;

    /** 日志表名（默认 sys_llm_invoke_log，一般无需修改） */
    private String tableName = "sys_llm_invoke_log";

    /** 异步落盘线程池核心线程数 */
    private int asyncPoolSize = 2;

    /** JDBC-Only 兜底数据源配置（未复用应用 DataSource 时使用） */
    private Jdbc jdbc = new Jdbc();

    /**
     * JDBC-Only 兜底数据源配置。
     */
    @Data
    public static class Jdbc {
        /** 是否启用 JDBC 直连模式（无 Spring DataSource 时） */
        private boolean enabled = false;

        /** JDBC URL，如 jdbc:postgresql://localhost:5432/app */
        private String jdbcUrl;

        /** 数据库用户名 */
        private String username;

        /** 数据库密码 */
        private String password;

        /** 连接池最大连接数 */
        private int maxPoolSize = 5;
    }
}