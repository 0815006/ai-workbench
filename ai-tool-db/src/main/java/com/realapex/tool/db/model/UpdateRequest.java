package com.realapex.tool.db.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * 受控写操作请求参数（{@code execute_update} 工具入参）。
 * <p>仅允许 INSERT / UPDATE / DELETE 三类 DML；高危 DDL/DCL 绝对拦截；
 * UPDATE/DELETE 必须带 WHERE 条件；触发 HITL 人工审批。</p>
 *
 * @param sql 写操作 SQL（INSERT / UPDATE / DELETE）
 */
public record UpdateRequest(
        @ToolParam(description = "写操作 SQL（仅允许 INSERT / UPDATE / DELETE，UPDATE/DELETE 必须带 WHERE 条件）", required = true)
        String sql
) {
}