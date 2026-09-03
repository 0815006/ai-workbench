package com.realapex.tool.db.model;

import java.util.List;

/**
 * 只读查询结果（{@code readonly_query} 工具出参）。
 *
 * @param columns   列名列表
 * @param rows      数据行（每行按列顺序的字符串值，NULL 为 null）
 * @param rowCount  实际返回行数
 * @param truncated 是否因 maxRows 截断
 */
public record QueryResult(
        List<String> columns,
        List<List<String>> rows,
        int rowCount,
        boolean truncated
) {
}