package com.realapex.tool.db.model;

/**
 * 慢查询日志条目（{@code fetch_slow_logs} 工具出参）。
 *
 * @param startTime   执行开始时间（ISO-8601）
 * @param durationMs  执行耗时（毫秒）
 * @param rowsExamined 扫描行数
 * @param rowsSent    返回行数
 * @param sql         慢 SQL 文本（可能被截断）
 * @param database    所属数据库（可空）
 */
public record SlowLogEntry(
        String startTime,
        long durationMs,
        long rowsExamined,
        long rowsSent,
        String sql,
        String database
) {
}