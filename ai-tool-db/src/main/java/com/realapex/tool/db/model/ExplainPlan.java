package com.realapex.tool.db.model;

import java.util.List;

/**
 * 执行计划（{@code explain_sql} 工具出参）。
 * <p>统一归一化 MySQL / TDSQL / GaussDB 的 EXPLAIN 输出列，屏蔽方言差异。</p>
 *
 * @param rows      执行计划行（每行一个 {@link ExplainRow}）
 * @param dialect   方言名称（mysql / tdsql / gaussdb）
 * @param rawOutput 原始 EXPLAIN 输出（调试用，可能被截断）
 */
public record ExplainPlan(
        List<ExplainRow> rows,
        String dialect,
        String rawOutput
) {

    /**
     * 单行执行计划。
     *
     * @param id           步骤 ID
     * @param selectType   SELECT 类型（SIMPLE / PRIMARY / SUBQUERY 等，GaussDB 可为空）
     * @param table        表名
     * @param type         访问类型（ALL / index / range / ref / eq_ref / const 等）
     * @param possibleKeys 可能使用的索引（逗号分隔，无则空串）
     * @param key          实际使用的索引（无则空串）
     * @param keyLen       索引长度
     * @param ref          关联列
     * @param rows         预估扫描行数
     * @param filtered     过滤比例（0-100，GaussDB 可为空）
     * @param extra        Extra 信息（Using index / Using where 等）
     */
    public record ExplainRow(
            String id,
            String selectType,
            String table,
            String type,
            String possibleKeys,
            String key,
            String keyLen,
            String ref,
            String rows,
            String filtered,
            String extra
    ) {
    }
}