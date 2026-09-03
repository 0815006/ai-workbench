package com.realapex.tool.db.dialect;

import com.realapex.tool.db.model.SlowLogFilter;

/**
 * 数据库方言策略接口——屏蔽 MySQL / TDSQL / GaussDB 在 EXPLAIN 语法、
 * 慢查询视图、Schema 系统表、LIMIT 改写与标识符转义上的差异。
 * <p>所有 SQL 生成与安全校验均通过本接口委托，上层工具不感知具体厂商。</p>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link MySqlDialect} — 标准 MySQL 语义（information_schema + 反引号 + LIMIT）</li>
 *   <li>{@link TdsqlDialect} — 继承 MySqlDialect，增强分片键校验（防广播全分片）</li>
 *   <li>{@link GaussDbDialect} — openGauss/PostgreSQL 语义（pg_catalog + 双引号 + FETCH FIRST）</li>
 * </ul>
 */
public interface DbDialect {

    /**
     * 方言名称（mysql / tdsql / gaussdb）。
     *
     * @return 方言名称
     */
    String name();

    /**
     * 生成执行计划查询语句（如 MySQL: {@code EXPLAIN {sql}}）。
     *
     * @param sql 原始 SQL
     * @return EXPLAIN 语句
     */
    String buildExplainSql(String sql);

    /**
     * 生成慢查询抓取 SQL（如 MySQL: {@code SELECT * FROM mysql.slow_log WHERE ...}）。
     *
     * @param filter 慢查询过滤条件
     * @return 慢查询 SQL
     */
    String buildSlowLogQuery(SlowLogFilter filter);

    /**
     * 生成表结构提取 SQL（information_schema / pg_catalog）。
     *
     * @param schema 数据库/模式名（可空）
     * @param table  表名（可空，空则返回表清单）
     * @return Schema 提取 SQL
     */
    String buildExtractSchemaSql(String schema, String table);

    /**
     * 为查询 SQL 强制注入/改写 LIMIT 限制（防全表扫描）。
     *
     * @param sql     原始 SQL
     * @param maxRows 最大行数
     * @return 带 LIMIT 的 SQL
     */
    String applyLimit(String sql, int maxRows);

    /**
     * 校验 SQL 是否包含危险关键字或缺失 WHERE 条件（方言级增强，如 TDSQL 分片键校验）。
     *
     * @param sql 待校验 SQL
     * @throws SecurityException SQL 不安全时抛出
     */
    void validateSqlSafety(String sql);

    /**
     * 标识符转义（MySQL: {@code `col`}，GaussDB: {@code "col"}）。
     *
     * @param identifier 标识符
     * @return 转义后的标识符
     */
    String quoteIdentifier(String identifier);
}