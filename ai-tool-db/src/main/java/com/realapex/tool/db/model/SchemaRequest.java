package com.realapex.tool.db.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * Schema 探查请求参数（{@code get_db_schema} 工具入参）。
 *
 * @param schema 数据库/模式名（MySQL 为 database，GaussDB 为 schema），可空表示当前库
 * @param table  表名，为空时返回库内表清单
 */
public record SchemaRequest(
        @ToolParam(description = "数据库/模式名（MySQL 为 database，GaussDB 为 schema），留空表示当前库", required = false)
        String schema,

        @ToolParam(description = "表名，留空时返回库内表清单", required = false)
        String table
) {
}