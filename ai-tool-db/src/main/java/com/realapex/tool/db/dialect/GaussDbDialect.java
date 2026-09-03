package com.realapex.tool.db.dialect;

import com.realapex.tool.db.model.SlowLogFilter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * GaussDB / openGauss 方言实现——基于 PostgreSQL 语义。
 * <p>使用 {@code pg_catalog} / {@code information_schema} 提取表结构、
 * {@code EXPLAIN (ANALYZE, COSTS) {sql}} 生成执行计划、
 * {@code dbe_perf.statement_history} 抓取慢查询、双引号转义标识符、
 * {@code LIMIT n} / {@code FETCH FIRST n ROWS ONLY} 分页保护。</p>
 *
 * <h3>连接池注意点</h3>
 * <ul>
 *   <li>JDBC 驱动建连较重，探活用 {@code SELECT 1} 或 JDBC4 {@code isValid()}</li>
 *   <li>失败事务归还前必须 {@code rollback()}，否则复用时报 {@code current transaction is aborted}</li>
 * </ul>
 */
@Slf4j
public class GaussDbDialect implements DbDialect {

    /** 高危 DDL/DCL 关键字（大小写不敏感） */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("\\bDROP\\s+(TABLE|DATABASE|SCHEMA|VIEW|INDEX)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bTRUNCATE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bGRANT\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bREVOKE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bALTER\\s+(TABLE|DATABASE|USER)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCREATE\\s+(TABLE|DATABASE|INDEX|USER|VIEW)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bVACUUM\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCLUSTER\\b", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "gaussdb";
    }

    @Override
    public String buildExplainSql(String sql) {
        return "EXPLAIN (ANALYZE, COSTS) " + sql;
    }

    @Override
    public String buildSlowLogQuery(SlowLogFilter filter) {
        // GaussDB 慢 SQL 视图：dbe_perf.statement_history
        StringBuilder sb = new StringBuilder("SELECT start_time, duration, n_tuples_fetched, n_tuples_returned, query FROM dbe_perf.statement_history WHERE 1=1");
        if (filter.startTime() != null && !filter.startTime().isBlank()) {
            sb.append(" AND start_time >= '").append(filter.startTime()).append("'");
        }
        if (filter.endTime() != null && !filter.endTime().isBlank()) {
            sb.append(" AND start_time <= '").append(filter.endTime()).append("'");
        }
        if (filter.minDurationMs() != null) {
            sb.append(" AND duration >= ").append(filter.minDurationMs() / 1000.0);
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            sb.append(" AND query LIKE '%").append(escapeLike(filter.keyword())).append("%'");
        }
        int limit = filter.limit() != null ? filter.limit() : 20;
        sb.append(" ORDER BY start_time DESC LIMIT ").append(Math.max(1, limit));
        return sb.toString();
    }

    @Override
    public String buildExtractSchemaSql(String schema, String table) {
        if (table == null || table.isBlank()) {
            // 表清单（pg_catalog）
            StringBuilder sb = new StringBuilder("SELECT tablename, obj_description(oid) AS comment FROM pg_catalog.pg_tables WHERE schemaname = ");
            sb.append(schema == null || schema.isBlank() ? "'public'" : "'" + schema + "'");
            sb.append(" ORDER BY tablename");
            return sb.toString();
        }
        // 单表结构（information_schema）
        String schemaExpr = schema == null || schema.isBlank() ? "'public'" : "'" + schema + "'";
        return "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns "
                + "WHERE table_schema = " + schemaExpr + " AND table_name = '" + table + "' ORDER BY ordinal_position";
    }

    @Override
    public String applyLimit(String sql, int maxRows) {
        String trimmed = sql.trim();
        if (Pattern.compile("\\bLIMIT\\s+\\d+", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()
                || Pattern.compile("\\bFETCH\\s+FIRST", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            return trimmed;
        }
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + " LIMIT " + maxRows;
    }

    @Override
    public void validateSqlSafety(String sql) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                throw new SecurityException("SQL 包含高危 DDL/DCL 关键字，已拦截: " + pattern.pattern());
            }
        }
        String upper = sql.toUpperCase();
        if ((upper.contains("UPDATE") || upper.contains("DELETE")) && !upper.contains(" WHERE ")) {
            throw new SecurityException("UPDATE/DELETE 必须带 WHERE 条件，禁止全表更新/删除");
        }
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * 转义 LIKE 通配符。
     *
     * @param keyword 原始关键字
     * @return 转义后的关键字
     */
    protected String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}