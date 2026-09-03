package com.realapex.tool.db.security;

import com.realapex.tool.db.model.ExplainRequest;
import com.realapex.tool.db.model.QueryRequest;
import com.realapex.tool.db.model.SchemaRequest;
import com.realapex.tool.db.model.SlowLogFilter;
import com.realapex.tool.security.ToolSecurityInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.DescribeStatement;
import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.ShowStatement;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;

/**
 * 只读 SQL 校验拦截器——JSqlParser 语法级只读校验（安全控制第一）。
 * <p>采用语法级 AST 解析而非简单字符串前缀匹配，从根本上杜绝拼接/注释/大小写绕过：</p>
 * <ul>
 *   <li>解析 AST 识别语句类型，无法通过 {@code SELECT} 前缀后拼接恶意语句绕过</li>
 *   <li>天然拦截多语句（{@code ;} 分隔）与注释注入（{@code --}、{@code /*} 块注释）</li>
 *   <li>对 {@code WITH ... SELECT}（CTE）等合法只读复杂查询正确放行</li>
 *   <li>识别并放行 {@code EXPLAIN} / {@code SHOW} / {@code DESCRIBE} 等方言只读语句</li>
 * </ul>
 *
 * <h3>允许的只读语句类型（AST 白名单）</h3>
 * <p>{@code Select}（含 WITH CTE）、{@code Explain}、{@code ShowStatement}、{@code Describe}。</p>
 *
 * <h3>拦截的写/危险语句类型（黑名单）</h3>
 * <p>{@code Insert}、{@code Update}、{@code Delete}、{@code Drop}、{@code Alter}、{@code Truncate}、
 * {@code Create}、{@code Grant}、{@code Revoke}、{@code Call}、{@code Exec}、{@code Merge}、
 * {@code Set}（含变量赋值）、多语句。</p>
 *
 * <p>优先级 5，先于通用链（ParamValidator=10 / DangerousCommandFilter=20 / TimeoutInterceptor=50）执行。
 * 仅注册在 ai-tool-db 内部，不影响 ai-tool-sdk 通用拦截器链。</p>
 */
@Slf4j
public class ReadOnlySqlInterceptor implements ToolSecurityInterceptor {

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public void before(String toolName, Object request) throws SecurityException {
        if (request == null) {
            return;
        }
        String sql = extractSql(request);
        if (sql == null || sql.isBlank()) {
            return;
        }
        validateReadOnly(sql, toolName);
    }

    /**
     * 从工具请求中提取待校验的 SQL 字符串。
     *
     * @param request 工具请求对象
     * @return SQL 字符串，非 SQL 请求返回 null
     */
    private String extractSql(Object request) {
        if (request instanceof QueryRequest q) {
            return q.sql();
        }
        if (request instanceof ExplainRequest e) {
            return e.sql();
        }
        if (request instanceof SchemaRequest s) {
            return null; // Schema 请求由工具内部生成 SQL，不在此校验
        }
        if (request instanceof SlowLogFilter f) {
            return null; // 慢查询过滤条件由方言生成 SQL，不在此校验
        }
        return null;
    }

    /**
     * 语法级只读校验：解析 AST 并检查语句类型白名单。
     *
     * @param sql      待校验 SQL
     * @param toolName 工具名称（用于异常信息）
     * @throws SecurityException 非只读语句或解析失败时抛出
     */
    private void validateReadOnly(String sql, String toolName) throws SecurityException {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select || statement instanceof ExplainStatement
                    || statement instanceof ShowStatement || statement instanceof ShowTablesStatement
                    || statement instanceof ShowIndexStatement || statement instanceof DescribeStatement) {
                return; // 只读白名单放行
            }
            throw new SecurityException("工具 [" + toolName + "] 仅允许只读 SQL（SELECT/EXPLAIN/SHOW/DESCRIBE），"
                    + "已拦截非只读语句: " + statement.getClass().getSimpleName());
        } catch (JSQLParserException e) {
            // 解析失败：可能是多语句（; 分隔）或语法错误，一律拦截
            throw new SecurityException("工具 [" + toolName + "] SQL 语法级校验失败（可能包含多语句或非法语法），已拦截: "
                    + e.getMessage(), e);
        }
    }
}