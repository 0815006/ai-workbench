package com.realapex.tool.db.tool;

import com.realapex.tool.annotation.Tool;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.dialect.DbDialect;
import com.realapex.tool.db.model.ExplainPlan;
import com.realapex.tool.db.model.ExplainRequest;
import com.realapex.tool.db.pool.JdbcExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行计划分析器（{@code explain_sql}）。
 * <p>获取指定 SQL 的 EXPLAIN 执行计划，协助慢 SQL 诊断（type / possible_keys / key / rows / Extra）。</p>
 *
 * <h3>方言差异</h3>
 * <ul>
 *   <li>MySQL：{@code EXPLAIN {sql}}</li>
 *   <li>GaussDB：{@code EXPLAIN (ANALYZE, COSTS) {sql}}，输出列不同，需方言归一化</li>
 * </ul>
 *
 * <h3>安全</h3>
 * <p>{@code readOnly = true}，仅允许 SELECT 类只读语句（由 {@code ReadOnlySqlInterceptor} 语法级校验），
 * 隔离生产查询。</p>
 */
@Slf4j
@Tool(name = "explain_sql",
        description = "获取 SQL 的 EXPLAIN 执行计划（type/possible_keys/key/rows/Extra），"
                + "协助慢 SQL 诊断（只读）",
        readOnly = true)
public class SqlExplainTool implements AgentTool<ExplainRequest, ExplainPlan> {

    private final DbToolConfig config;
    private final JdbcExecutor executor;

    /**
     * 构造执行计划分析器。
     *
     * @param config   数据库工具配置
     * @param executor JDBC 统一执行封装
     */
    public SqlExplainTool(DbToolConfig config, JdbcExecutor executor) {
        this.config = config;
        this.executor = executor;
    }

    @Override
    public String name() {
        return "explain_sql";
    }

    @Override
    public String description() {
        return "获取 SQL 的 EXPLAIN 执行计划（type/possible_keys/key/rows/Extra），"
                + "协助慢 SQL 诊断（只读）";
    }

    @Override
    public Class<ExplainRequest> requestClass() {
        return ExplainRequest.class;
    }

    @Override
    public ExplainPlan execute(ExplainRequest request) throws Exception {
        DataSource ds = config.effectiveDataSource();
        if (ds == null) {
            throw new IllegalStateException("未配置数据源（模式 A 需注入 DataSource，模式 B 需通过 DbConnectionManager 注册）");
        }
        DbDialect dialect = config.effectiveDialect();

        // 方言生成 EXPLAIN 前缀
        String explainSql = dialect.buildExplainSql(request.sql());
        log.info("explain_sql: {}", explainSql);
        return executor.query(ds, explainSql, this::mapPlan);
    }

    /**
     * 执行计划归一化映射（屏蔽 MySQL / GaussDB 输出列差异）。
     *
     * @param rs 结果集
     * @return 归一化执行计划
     * @throws SQLException 读取失败时抛出
     */
    private ExplainPlan mapPlan(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<ExplainPlan.ExplainRow> rows = new ArrayList<>();
        StringBuilder raw = new StringBuilder();

        while (rs.next()) {
            // 归一化：按列名匹配（MySQL: id/select_type/table/type/possible_keys/key/key_len/ref/rows/filtered/Extra）
            // GaussDB: 列名不同，回退按位置读取
            String id = getString(rs, "id", 1);
            String selectType = getString(rs, "select_type", 2);
            String table = getString(rs, "table", 3);
            String type = getString(rs, "type", 4);
            String possibleKeys = getString(rs, "possible_keys", 5);
            String key = getString(rs, "key", 6);
            String keyLen = getString(rs, "key_len", 7);
            String ref = getString(rs, "ref", 8);
            String rowCount = getString(rs, "rows", 9);
            String filtered = getString(rs, "filtered", 10);
            String extra = getString(rs, "Extra", 11);

            rows.add(new ExplainPlan.ExplainRow(id, selectType, table, type,
                    possibleKeys, key, keyLen, ref, rowCount, filtered, extra));

            // 原始输出（调试用）
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    raw.append(" | ");
                }
                raw.append(rs.getString(i));
            }
            raw.append('\n');
        }
        return new ExplainPlan(rows, config.effectiveDialect().name(),
                OutputTruncator.truncate(raw.toString(), config.getMaxOutputChars()));
    }

    /**
     * 按列名取值，列不存在时回退按位置取值。
     *
     * @param rs        结果集
     * @param columnName 列名
     * @param fallbackIndex 回退位置（1-based）
     * @return 列值（null 转为空串）
     * @throws SQLException 读取失败时抛出
     */
    private String getString(ResultSet rs, String columnName, int fallbackIndex) throws SQLException {
        try {
            String value = rs.getString(columnName);
            return value == null ? "" : value;
        } catch (SQLException e) {
            // 列不存在（GaussDB 列名不同），回退按位置读取
            String value = rs.getString(fallbackIndex);
            return value == null ? "" : value;
        }
    }
}