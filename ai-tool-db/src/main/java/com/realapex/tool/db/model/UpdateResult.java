package com.realapex.tool.db.model;

/**
 * 受控写操作结果（{@code execute_update} 工具出参）。
 *
 * @param affectedRows 影响行数
 * @param sql          实际执行的 SQL
 * @param message      执行说明（如 "INSERT 成功"）
 */
public record UpdateResult(
        int affectedRows,
        String sql,
        String message
) {
}