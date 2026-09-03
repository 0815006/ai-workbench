package com.realapex.tool.db.model;

import java.util.List;

/**
 * 表结构摘要（{@code get_db_schema} 工具出参）。
 * <p>统一承载字段列表、索引信息、主外键与表注释，屏蔽 MySQL / TDSQL / GaussDB 方言差异。</p>
 *
 * @param schema      所属数据库/模式名
 * @param table       表名
 * @param comment     表注释（无则空串）
 * @param columns     字段列表（{@link ColumnInfo}）
 * @param indexes     索引列表（{@link IndexInfo}）
 * @param primaryKeys 主键字段名列表
 * @param foreignKeys 外键描述列表（如 "fk_order_user: user_id -> user.id"）
 */
public record TableSchema(
        String schema,
        String table,
        String comment,
        List<ColumnInfo> columns,
        List<IndexInfo> indexes,
        List<String> primaryKeys,
        List<String> foreignKeys
) {

    /**
     * 字段信息。
     *
     * @param name    字段名
     * @param type    字段类型（含长度，如 varchar(64)）
     * @param nullable 是否允许 NULL
     * @param key     键类型（PRI / UNI / MUL / 空串）
     * @param defaultValue 默认值（无则 null）
     * @param comment 字段注释（无则空串）
     */
    public record ColumnInfo(
            String name,
            String type,
            boolean nullable,
            String key,
            String defaultValue,
            String comment
    ) {
    }

    /**
     * 索引信息。
     *
     * @param name    索引名
     * @param columns 索引字段列表
     * @param unique  是否唯一索引
     * @param type    索引类型（BTREE / HASH / 空串）
     */
    public record IndexInfo(
            String name,
            List<String> columns,
            boolean unique,
            String type
    ) {
    }
}