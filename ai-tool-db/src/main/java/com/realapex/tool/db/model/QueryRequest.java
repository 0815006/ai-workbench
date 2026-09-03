package com.realapex.tool.db.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * 只读查询请求参数（{@code readonly_query} 工具入参）。
 *
 * @param sql     只读 SQL（SELECT / SHOW / DESCRIBE / EXPLAIN），语法级校验
 * @param maxRows 最大返回行数（可选，受全局 {@code maxRows} 上限约束，默认 100）
 */
public record QueryRequest(
        @ToolParam(description = "只读 SQL 语句（SELECT / SHOW / DESCRIBE / EXPLAIN），语法级只读校验", required = true)
        String sql,

        @ToolParam(description = "最大返回行数（可选，受全局上限约束，默认 100）", required = false)
        Integer maxRows
) {
}