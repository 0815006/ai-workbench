package com.realapex.tool.db.tool;

import com.realapex.tool.annotation.Tool;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.model.UpdateRequest;
import com.realapex.tool.db.model.UpdateResult;
import com.realapex.tool.db.pool.JdbcExecutor;
import com.realapex.tool.db.security.SqlSafetyChecker;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

/**
 * 受控写操作执行器（{@code execute_update}）。
 * <p>执行数据变更（INSERT / UPDATE / DELETE），受安全卡口 + HITL 双重约束。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li><b>{@code requiresApproval = true}</b>：触发 HITL 人工审批（配合 ai-agent-sdk 的\n
 *       {@code AgentSuspendedException} + {@code AgentRunner.resume} 挂起/恢复机制）</li>
 *   <li><b>强制 WHERE 条件校验</b>：UPDATE/DELETE 无条件 WHERE 直接阻断</li>
 *   <li><b>影响行数上限卡口</b>：超过 {@code maxAffectedRows}（默认 500）阻断</li>
 *   <li><b>高危 DDL/DCL 绝对拦截</b>：DROP / TRUNCATE / GRANT / REVOKE 等直接阻断</li>
 *   <li><b>方言级增强</b>：TDSQL 分片键校验，防广播全分片</li>
 * </ul>
 *
 * <p>典型 ReAct 路径：{@code get_db_schema} 探查结构 → {@code readonly_query} 验证假设 →\n
 * {@code explain_sql} 诊断慢 SQL → {@code execute_update} 受控变更（HITL 审批）→\n
 * {@code fetch_slow_logs} 复盘。</p>
 */
@Slf4j
@Tool(name = "execute_update",
        description = "执行受控数据变更（INSERT/UPDATE/DELETE），需人工审批（HITL），"
                + "强制 WHERE 条件与影响行数上限卡口",
        readOnly = false,
        requiresApproval = true)
public class ExecuteUpdateTool implements AgentTool<UpdateRequest, UpdateResult> {

    private final DbToolConfig config;
    private final JdbcExecutor executor;
    private final SqlSafetyChecker safetyChecker;

    /**
     * 构造受控写操作执行器。
     *
     * @param config        数据库工具配置
     * @param executor      JDBC 统一执行封装
     * @param safetyChecker 写操作安全卡口
     */
    public ExecuteUpdateTool(DbToolConfig config, JdbcExecutor executor, SqlSafetyChecker safetyChecker) {
        this.config = config;
        this.executor = executor;
        this.safetyChecker = safetyChecker;
    }

    @Override
    public String name() {
        return "execute_update";
    }

    @Override
    public String description() {
        return "执行受控数据变更（INSERT/UPDATE/DELETE），需人工审批（HITL），"
                + "强制 WHERE 条件与影响行数上限卡口";
    }

    @Override
    public Class<UpdateRequest> requestClass() {
        return UpdateRequest.class;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public UpdateResult execute(UpdateRequest request) throws Exception {
        DataSource ds = config.effectiveDataSource();
        if (ds == null) {
            throw new IllegalStateException("未配置数据源（模式 A 需注入 DataSource，模式 B 需通过 DbConnectionManager 注册）");
        }

        // 1. 写操作安全卡口（WHERE / DDL / 方言增强）
        safetyChecker.validateUpdate(request);

        // 2. 执行写操作（手动提交 + 异常回滚）
        int affected = executor.update(ds, request.sql());

        // 3. 影响行数上限卡口
        safetyChecker.validateAffectedRows(affected);

        log.info("execute_update: affectedRows={}, sql={}", affected, request.sql());
        return new UpdateResult(affected, request.sql(), "执行成功，影响 " + affected + " 行");
    }
}