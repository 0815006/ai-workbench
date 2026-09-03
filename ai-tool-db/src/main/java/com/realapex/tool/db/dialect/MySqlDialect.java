package com.realapex.tool.db.dialect;

import com.realapex.tool.db.model.SlowLogFilter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * MySQL 方言实现——标准 MySQL 语义。
 * <p>基于 {@code information_schema} 提取表结构、{@code EXPLAIN {sql}} 生成执行计划、
 * {@code mysql.slow_log} 抓取慢查询、反引号转义标识符、{@code LIMIT n} 分页保护。</p>
 *
 * <h3>安全校验</h3>
 * <ul>
 *   <li>高危 DDL/DCL 关键字绝对拦截（DROP / TRUNCATE / GRANT / REVOKE 等）</li>
 *   <li>UPDATE/DELETE 无条件 WHERE 阻断</li>
 * </ul>
 */
@Slf4j
public class MySqlDialect implements DbDialect {

    /** 高危 DDL/DCL 关键字（大小写不敏感） */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("\\bDROP\\s+(TABLE|DATABASE|SCHEMA|VIEW|INDEX)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bTRUNCATE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bGRANT\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bREVOKE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bALTER\\s+(TABLE|DATABASE|USER)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCREATE\\s+(TABLE|DATABASE|INDEX|USER|VIEW)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bRENAME\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bLOCK\\s+TABLES\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bKILL\\b", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String buildExplainSql(String sql) {
        return "EXPLAIN " + sql;
    }

    @Override
    public String buildSlowLogQuery(SlowLogFilter filter) {
        StringBuilder sb = new StringBuilder("SELECT start_time, query_time, rows_examined, rows_sent, db, sql_text FROM mysql.slow_log WHERE 1=1");
        if (filter.startTime() != null && !filter.startTime().isBlank()) {
            sb.append(" AND start_time >= '").append(filter.startTime()).append("'");
        }
        if (filter.endTime() != null && !filter.endTime().isBlank()) {
            sb.append(" AND start_time <= '").append(filter.endTime()).append("'");
        }
        if (filter.minDurationMs() != null) {
            sb.append(" AND query_time >= ").append(filter.minDurationMs() / 1000.0);
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            sb.append(" AND sql_text LIKE '%").append(escapeLike(filter.keyword())).append("%'");
        }
        int limit = filter.limit() != null ? filter.limit() : 20;
        sb.append(" ORDER BY start_time DESC LIMIT ").append(Math.max(1, limit));
        return sb.toString();
    }

    @Override
    public String buildExtractSchemaSql(String schema, String table) {
        if (table == null || table.isBlank()) {
            // 表清单
            StringBuilder sb = new StringBuilder("SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = ");
            sb.append(schema == null || schema.isBlank() ? "DATABASE()" : "'" + schema + "'");
            sb.append(" ORDER BY TABLE_NAME");
            return sb.toString();
        }
        // 单表结构：字段 + 索引 + 主键 + 外键
        String schemaExpr = schema == null || schema.isBlank() ? "DATABASE()" : "'" + schema + "'";
        return "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, COLUMN_COMMENT "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = " + schemaExpr
                + " AND TABLE_NAME = '" + table + "' ORDER BY ORDINAL_POSITION";
    }

    @Override
    public String applyLimit(String sql, int maxRows) {
        String trimmed = sql.trim();
        // 已带 LIMIT 的 SQL 不重复注入（保留原 LIMIT，但由 maxRows 兜底截断）
        if (Pattern.compile("\\bLIMIT\\s+\\d+", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            return trimmed;
        }
        // 去掉末尾分号后追加 LIMIT
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
        // UPDATE/DELETE 无条件 WHERE 阻断
        String upper = sql.toUpperCase();
        if ((upper.contains("UPDATE") || upper.contains("DELETE")) && !upper.contains(" WHERE ")) {
            throw new SecurityException("UPDATE/DELETE 必须带 WHERE 条件，禁止全表更新/删除");
        }
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 转义 LIKE 通配符（% / _ / \）。
     *
     * @param keyword 原始关键字
     * @return 转义后的关键字
     */
    protected String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 拼接慢查询过滤条件（供子类复用）。
     *
     * @param filter 过滤条件
     * @return WHERE 条件片段列表
     */
    protected List<String> buildSlowLogConditions(SlowLogFilter filter) {
        List<String> conditions = new ArrayList<>();
        if (filter.startTime() != null && !filter.startTime().isBlank()) {
            conditions.add("start_time >= '" + filter.startTime() + "'");
        }
        if (filter.endTime() != null && !filter.endTime().isBlank()) {
            conditions.add("start_time <= '" + filter.endTime() + "'");
        }
        if (filter.minDurationMs() != null) {
            conditions.add("query_time >= " + (filter.minDurationMs() / 1000.0));
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            conditions.add("sql_text LIKE '%" + escapeLike(filter.keyword()) + "%'");
        }
        return conditions;
    }
}