package com.realapex.tool.db.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * 执行计划分析请求参数（{@code explain_sql} 工具入参）。
 *
 * @param sql 待诊断 SQL（仅允许 SELECT 类只读语句，由方言生成 EXPLAIN 前缀）
 */
public record ExplainRequest(
        @ToolParam(description = "待诊断 SQL（SELECT 类只读语句，自动加 EXPLAIN 前缀）", required = true)
        String sql
) {
}