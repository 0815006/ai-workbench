package com.realapex.tool.db.tool;

import com.realapex.tool.annotation.Tool;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.dialect.DbDialect;
import com.realapex.tool.db.model.QueryRequest;
import com.realapex.tool.db.model.QueryResult;
import com.realapex.tool.db.pool.JdbcExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 只读查询执行器（{@code readonly_query}）。
 * <p>执行只读 SELECT / SHOW 查询（用于探查数据、验证假设），自动截断 + 强制 LIMIT 保护。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>{@code readOnly = true}，配合 {@code ReadOnlySqlInterceptor} 做 JSqlParser 语法级只读校验</li>
 *   <li>方言 {@code applyLimit} 强制注入 {@code LIMIT} / {@code FETCH FIRST}，防全表扫描</li>
 *   <li>Statement 级超时强杀（默认 10s）+ 结果集 Token 截断（默认 20,000 字符）</li>
 * </ul>
 */
@Slf4j
@Tool(name = "readonly_query",
        description = "执行只读 SQL 查询（SELECT/SHOW/DESCRIBE），自动强制 LIMIT 与超时强杀，"
                + "用于探查数据、验证假设（只读）",
        readOnly = true)
public class ReadOnlyQueryTool implements AgentTool<QueryRequest, QueryResult> {

    private final DbToolConfig config;
    private final JdbcExecutor executor;

    /**
     * 构造只读查询执行器。
     *
     * @param config   数据库工具配置
     * @param executor JDBC 统一执行封装
     */
    public ReadOnlyQueryTool(DbToolConfig config, JdbcExecutor executor) {
        this.config = config;
        this.executor = executor;
    }

    @Override
    public String name() {
        return "readonly_query";
    }

    @Override
    public String description() {
        return "执行只读 SQL 查询（SELECT/SHOW/DESCRIBE），自动强制 LIMIT 与超时强杀，"
                + "用于探查数据、验证假设（只读）";
    }

    @Override
    public Class<QueryRequest> requestClass() {
        return QueryRequest.class;
    }

    @Override
    public QueryResult execute(QueryRequest request) throws Exception {
        DataSource ds = config.effectiveDataSource();
        if (ds == null) {
            throw new IllegalStateException("未配置数据源（模式 A 需注入 DataSource，模式 B 需通过 DbConnectionManager 注册）");
        }
        DbDialect dialect = config.effectiveDialect();

        // 1. 强制 LIMIT 保护（方言级注入，防全表扫描）
        int maxRows = request.maxRows() != null ? request.maxRows() : config.getMaxRows();
        String limitedSql = dialect.applyLimit(request.sql(), maxRows);

        log.info("readonly_query: sql={}, maxRows={}", limitedSql, maxRows);
        return executor.query(ds, limitedSql, maxRows, this::mapResult);
    }

    /**
     * 结果集映射：列名 + 数据行（自动截断）。
     *
     * @param rs 结果集
     * @return 查询结果
     * @throws SQLException 读取失败时抛出
     */
    private QueryResult mapResult(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= config.getMaxRows()) {
                truncated = true;
                break;
            }
            List<String> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getString(i));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, rows.size(), truncated);
    }
}