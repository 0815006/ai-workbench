package com.realapex.tool.db.tool;

import com.realapex.tool.annotation.Tool;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.dialect.DbDialect;
import com.realapex.tool.db.model.SlowLogEntry;
import com.realapex.tool.db.model.SlowLogFilter;
import com.realapex.tool.db.pool.JdbcExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 慢查询抓取器（{@code fetch_slow_logs}）。
 * <p>按时间窗/阈值/关键字抓取慢查询日志，输出含 SQL 文本、耗时、扫描行数、执行时间。</p>
 *
 * <h3>方言差异</h3>
 * <ul>
 *   <li>MySQL：{@code mysql.slow_log} / {@code performance_schema}</li>
 *   <li>TDSQL：分片慢日志聚合视图（兼容 MySQL 字段命名）</li>
 *   <li>GaussDB：{@code dbe_perf.statement_history} 慢 SQL 视图</li>
 * </ul>
 *
 * <h3>安全</h3>
 * <p>{@code readOnly = true}，只读工具。</p>
 */
@Slf4j
@Tool(name = "fetch_slow_logs",
        description = "按时间窗/阈值/关键字抓取慢查询日志（SQL 文本、耗时、扫描行数），"
                + "用于慢 SQL 复盘与优化（只读）",
        readOnly = true)
public class SlowLogFetcherTool implements AgentTool<SlowLogFilter, List<SlowLogEntry>> {

    private final DbToolConfig config;
    private final JdbcExecutor executor;

    /**
     * 构造慢查询抓取器。
     *
     * @param config   数据库工具配置
     * @param executor JDBC 统一执行封装
     */
    public SlowLogFetcherTool(DbToolConfig config, JdbcExecutor executor) {
        this.config = config;
        this.executor = executor;
    }

    @Override
    public String name() {
        return "fetch_slow_logs";
    }

    @Override
    public String description() {
        return "按时间窗/阈值/关键字抓取慢查询日志（SQL 文本、耗时、扫描行数），"
                + "用于慢 SQL 复盘与优化（只读）";
    }

    @Override
    public Class<SlowLogFilter> requestClass() {
        return SlowLogFilter.class;
    }

    @Override
    public List<SlowLogEntry> execute(SlowLogFilter filter) throws Exception {
        DataSource ds = config.effectiveDataSource();
        if (ds == null) {
            throw new IllegalStateException("未配置数据源（模式 A 需注入 DataSource，模式 B 需通过 DbConnectionManager 注册）");
        }
        DbDialect dialect = config.effectiveDialect();

        // 方言生成慢查询抓取 SQL
        String sql = dialect.buildSlowLogQuery(filter);
        log.info("fetch_slow_logs: {}", sql);
        return executor.query(ds, sql, this::mapEntries);
    }

    /**
     * 慢查询条目映射（兼容 MySQL / TDSQL / GaussDB 字段命名差异）。
     *
     * @param rs 结果集
     * @return 慢查询条目列表
     * @throws SQLException 读取失败时抛出
     */
    private List<SlowLogEntry> mapEntries(ResultSet rs) throws SQLException {
        List<SlowLogEntry> entries = new ArrayList<>();
        while (rs.next()) {
            // MySQL/TDSQL: start_time, query_time, rows_examined, rows_sent, db, sql_text
            // GaussDB: start_time, duration, n_tuples_fetched, n_tuples_returned, query
            String startTime = getString(rs, "start_time", null, 1);
            long durationMs = getLong(rs, "query_time", "duration", 2);
            long rowsExamined = getLong(rs, "rows_examined", "n_tuples_fetched", 3);
            long rowsSent = getLong(rs, "rows_sent", "n_tuples_returned", 4);
            String database = getString(rs, "db", null, 5);
            String sql = getString(rs, "sql_text", "query", 6);

            entries.add(new SlowLogEntry(startTime, durationMs, rowsExamined, rowsSent,
                    OutputTruncator.truncate(sql, config.getMaxOutputChars()), database));
        }
        return entries;
    }

    /**
     * 按列名取值（支持别名回退），列不存在时按位置取值。
     *
     * @param rs        结果集
     * @param columnName 首选列名
     * @param fallbackName 回退列名（可为 null）
     * @param fallbackIndex 回退位置（1-based）
     * @return 列值（null 转为空串）
     * @throws SQLException 读取失败时抛出
     */
    private String getString(ResultSet rs, String columnName, String fallbackName, int fallbackIndex) throws SQLException {
        try {
            String value = rs.getString(columnName);
            return value == null ? "" : value;
        } catch (SQLException e) {
            if (fallbackName != null) {
                try {
                    String value = rs.getString(fallbackName);
                    return value == null ? "" : value;
                } catch (SQLException ignored) {
                    // 继续回退到位置
                }
            }
            String value = rs.getString(fallbackIndex);
            return value == null ? "" : value;
        }
    }

    /**
     * 按列名取 long 值（支持别名回退），列不存在时按位置取值。
     *
     * @param rs        结果集
     * @param columnName 首选列名
     * @param fallbackName 回退列名（可为 null）
     * @param fallbackIndex 回退位置（1-based）
     * @return long 值（null 转为 0）
     * @throws SQLException 读取失败时抛出
     */
    private long getLong(ResultSet rs, String columnName, String fallbackName, int fallbackIndex) throws SQLException {
        try {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? 0 : value;
        } catch (SQLException e) {
            if (fallbackName != null) {
                try {
                    long value = rs.getLong(fallbackName);
                    return rs.wasNull() ? 0 : value;
                } catch (SQLException ignored) {
                    // 继续回退到位置
                }
            }
            long value = rs.getLong(fallbackIndex);
            return rs.wasNull() ? 0 : value;
        }
    }
}