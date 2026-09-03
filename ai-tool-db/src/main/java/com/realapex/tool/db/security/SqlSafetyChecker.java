package com.realapex.tool.db.security;

import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.model.UpdateRequest;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 写操作安全卡口——{@code execute_update} 工具专属业务级校验。
 * <p>在 {@link ReadOnlySqlInterceptor} 之外叠加写操作专属安全卡口，基于 JSqlParser AST 校验：</p>
 * <ul>
 *   <li><b>高危 DDL/DCL 绝对拦截</b>：{@code DROP DATABASE} / {@code DROP TABLE} / {@code TRUNCATE} /
 *       {@code GRANT} / {@code REVOKE} 等直接阻断</li>
 *   <li><b>无条件 WHERE 阻断</b>：{@code UPDATE} / {@code DELETE} 无 WHERE 条件 → 阻断，
 *       禁止全表更新/删除</li>
 *   <li><b>影响行数上限卡口</b>：变更影响行数超过 {@code maxAffectedRows}（默认 500）→ 阻断并提示缩小范围</li>
 *   <li><b>方言级安全增强</b>：委托 {@code dialect.validateSqlSafety} 做方言增强（如 TDSQL 分片键校验）</li>
 * </ul>
 *
 * <p>仅允许 INSERT / UPDATE / DELETE 三类 DML；其余语句（DDL/DCL/多语句）一律拦截。</p>
 */
@Slf4j
public class SqlSafetyChecker {

    private final DbToolConfig config;

    /**
     * 构造写操作安全卡口。
     *
     * @param config 数据库工具配置（影响行数上限、方言）
     */
    public SqlSafetyChecker(DbToolConfig config) {
        this.config = config;
    }

    /**
     * 基于 JSqlParser AST 做写操作安全校验。
     *
     * @param request 写操作请求
     * @throws SecurityException 校验不通过时抛出
     */
    public void validateUpdate(UpdateRequest request) throws SecurityException {
        String sql = request.sql();
        if (sql == null || sql.isBlank()) {
            throw new SecurityException("写操作 SQL 不能为空");
        }

        // 1. 解析 AST，识别 DML 类型
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new SecurityException("写操作 SQL 语法级校验失败（可能包含多语句或非法语法），已拦截: " + e.getMessage(), e);
        }

        // 2. 仅允许 INSERT / UPDATE / DELETE 三类 DML
        if (!(statement instanceof Insert || statement instanceof Update || statement instanceof Delete)) {
            throw new SecurityException("execute_update 仅允许 INSERT/UPDATE/DELETE 三类 DML，"
                    + "已拦截高危语句: " + statement.getClass().getSimpleName());
        }

        // 3. UPDATE/DELETE 无条件 WHERE 阻断
        if (statement instanceof Update update && update.getWhere() == null) {
            throw new SecurityException("UPDATE 必须带 WHERE 条件，禁止全表更新");
        }
        if (statement instanceof Delete delete && delete.getWhere() == null) {
            throw new SecurityException("DELETE 必须带 WHERE 条件，禁止全表删除");
        }

        // 4. 委托方言做方言级安全增强（如 TDSQL 分片键校验）
        config.effectiveDialect().validateSqlSafety(sql);
    }

    /**
     * 校验影响行数是否超过上限卡口。
     *
     * @param affectedRows 实际影响行数
     * @throws SecurityException 超过上限时抛出
     */
    public void validateAffectedRows(int affectedRows) throws SecurityException {
        if (affectedRows > config.getMaxAffectedRows()) {
            throw new SecurityException("写操作影响行数 " + affectedRows + " 超过上限 "
                    + config.getMaxAffectedRows() + "，请缩小变更范围（增加 WHERE 条件或分批执行）");
        }
    }
}