package com.realapex.tool.db.config;

import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.pool.DbConnectionManager;
import com.realapex.tool.db.pool.JdbcExecutor;
import com.realapex.tool.db.security.ReadOnlySqlInterceptor;
import com.realapex.tool.db.security.SqlSafetyChecker;
import com.realapex.tool.db.tool.ExecuteUpdateTool;
import com.realapex.tool.db.tool.GetDbSchemaTool;
import com.realapex.tool.db.tool.ReadOnlyQueryTool;
import com.realapex.tool.db.tool.SlowLogFetcherTool;
import com.realapex.tool.db.tool.SqlExplainTool;
import com.realapex.tool.security.ToolSecurityInterceptor;

import java.util.List;

/**
 * 数据库工具工厂——一键挂载 ai-tool-db 工具组（Tool Set）。
 * <p>与 {@code ai-tool-sdk} 的 {@code BaseToolFactory} 风格一致：
 * 宿主应用无需逐个 {@code new} 工具，直接调用工厂方法即可获得带安全防护的完整数据库工具组。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 模式 A：复用已有 DataSource
 * List<AgentTool<?, ?>> dbTools = DbToolFactory.createDbTools(
 *         DbToolConfig.builder().dataSource(ds).build());
 *
 * // 模式 B：动态注册托管数据源
 * DbConnectionManager.register("test-db", DbToolConfig.builder()
 *         .jdbcUrl("jdbc:mysql://localhost:3306/demo")
 *         .username("root").password("secret").build());
 * List<AgentTool<?, ?>> dbTools = DbToolFactory.createDbTools(
 *         DbConnectionManager.resolve("test-db"), new MySqlDialect());
 * }</pre>
 */
public final class DbToolFactory {

    private DbToolFactory() {
    }

    /**
     * 创建数据库工具组（5 个原子工具）。
     * <p>包含：{@code get_db_schema}（Schema 探查）、{@code readonly_query}（只读查询）、
     * {@code explain_sql}（执行计划诊断）、{@code fetch_slow_logs}（慢查询抓取）、
     * {@code execute_update}（受控写操作，HITL 审批）。</p>
     *
     * @param config 数据库工具配置（数据源/方言/截断/超时/行数卡口）
     * @return 数据库工具列表
     */
    public static List<AgentTool<?, ?>> createDbTools(DbToolConfig config) {
        JdbcExecutor executor = new JdbcExecutor(config);
        SqlSafetyChecker safetyChecker = new SqlSafetyChecker(config);
        return List.of(
                new GetDbSchemaTool(config, executor),
                new ReadOnlyQueryTool(config, executor),
                new SqlExplainTool(config, executor),
                new SlowLogFetcherTool(config, executor),
                new ExecuteUpdateTool(config, executor, safetyChecker)
        );
    }

    /**
     * 创建数据库工具组（模式 A 便捷重载：外部注入 DataSource + 显式方言）。
     *
     * @param dataSource 应用层自建 DataSource
     * @param dialect    数据库方言
     * @return 数据库工具列表
     */
    public static List<AgentTool<?, ?>> createDbTools(javax.sql.DataSource dataSource,
                                                      com.realapex.tool.db.dialect.DbDialect dialect) {
        DbToolConfig config = DbToolConfig.builder()
                .dataSource(dataSource)
                .dialect(dialect)
                .build();
        return createDbTools(config);
    }

    /**
     * 创建数据库领域专属只读 SQL 拦截器。
     * <p>优先级 5，先于通用链（ParamValidator=10 / DangerousCommandFilter=20 / TimeoutInterceptor=50）
     * 执行，基于 JSqlParser 语法级解析做只读校验。</p>
     *
     * @return 只读 SQL 拦截器
     */
    public static ToolSecurityInterceptor createReadOnlyInterceptor() {
        return new ReadOnlySqlInterceptor();
    }
}