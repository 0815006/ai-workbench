package com.realapex.tool.db.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * 慢查询抓取过滤条件（{@code fetch_slow_logs} 工具入参）。
 *
 * @param startTime     开始时间（ISO-8601，如 2026-09-01T00:00:00），可空
 * @param endTime       结束时间（ISO-8601），可空
 * @param minDurationMs 最小耗时阈值（毫秒），可空
 * @param keyword       SQL 关键字过滤（可空）
 * @param limit         最大返回条数（默认 20，受全局 maxRows 约束）
 */
public record SlowLogFilter(
        @ToolParam(description = "开始时间（ISO-8601，如 2026-09-01T00:00:00），可空", required = false)
        String startTime,

        @ToolParam(description = "结束时间（ISO-8601），可空", required = false)
        String endTime,

        @ToolParam(description = "最小耗时阈值（毫秒），可空", required = false)
        Long minDurationMs,

        @ToolParam(description = "SQL 关键字过滤，可空", required = false)
        String keyword,

        @ToolParam(description = "最大返回条数（默认 20）", required = false)
        Integer limit
) {
}