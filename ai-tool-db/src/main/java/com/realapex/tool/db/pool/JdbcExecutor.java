package com.realapex.tool.db.pool;

import com.realapex.tool.db.config.DbToolConfig;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC 统一执行封装——Statement 级超时/行数/回滚的防泄露铁律。
 * <p>连接池只能解决「连接的借与还」，解决不了「Agent 生成慢 SQL / 笛卡尔积导致连接卡死阻塞」。
 * 本类强制叠加三层防护：</p>
 * <ul>
 *   <li><b>语句级超时</b>：{@code setQueryTimeout}（默认 10s），慢 SQL 强杀，防拖垮连接池</li>
 *   <li><b>行数上限</b>：{@code setMaxRows}（默认 100），游标读取防 OOM</li>
 *   <li><b>try-with-resources</b>：无条件归还连接；写操作异常时 {@code rollback()} 防锁表</li>
 * </ul>
 *
 * <h3>防泄露铁律</h3>
 * <ul>
 *   <li>统一通过 try-with-resources 获取/释放 {@code Connection}，绝不裸露给外部</li>
 *   <li>写操作开启手动提交，异常时 {@code rollback()}，避免未提交事务卡死锁表</li>
 * </ul>
 */
@Slf4j
public final class JdbcExecutor {

    private final DbToolConfig config;

    /**
     * 构造执行器。
     *
     * @param config 数据库工具配置（超时/行数卡口）
     */
    public JdbcExecutor(DbToolConfig config) {
        this.config = config;
    }

    /**
     * 执行只读查询并映射结果。
     *
     * @param ds     数据源
     * @param sql    只读 SQL
     * @param mapper 结果集映射器
     * @param <T>    映射结果类型
     * @return 映射结果
     * @throws SQLException 执行失败时抛出
     */
    public <T> T query(DataSource ds, String sql, SqlResultMapper<T> mapper) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 1. 语句级超时（默认 10s），防止慢 SQL 拖垮连接池
            stmt.setQueryTimeout(config.getQueryTimeoutSeconds());

            // 2. 游标读取 + maxRows 保护内存，防止 OOM
            stmt.setMaxRows(config.getMaxRows());

            try (ResultSet rs = stmt.executeQuery()) {
                return mapper.map(rs);
            }
        } // try-with-resources 自动 close，无条件归还连接给池子
    }

    /**
     * 执行只读查询并映射结果（自定义行数上限，受全局 maxRows 约束）。
     *
     * @param ds      数据源
     * @param sql     只读 SQL
     * @param maxRows 最大返回行数（超过全局上限时按全局上限截断）
     * @param mapper  结果集映射器
     * @param <T>     映射结果类型
     * @return 映射结果
     * @throws SQLException 执行失败时抛出
     */
    public <T> T query(DataSource ds, String sql, int maxRows, SqlResultMapper<T> mapper) throws SQLException {
        int effectiveMaxRows = Math.min(maxRows > 0 ? maxRows : config.getMaxRows(), config.getMaxRows());
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setQueryTimeout(config.getQueryTimeoutSeconds());
            stmt.setMaxRows(effectiveMaxRows);

            try (ResultSet rs = stmt.executeQuery()) {
                return mapper.map(rs);
            }
        }
    }

    /**
     * 执行写操作（INSERT/UPDATE/DELETE），返回影响行数。
     * <p>开启手动提交，异常时 {@code rollback()} 防未提交事务卡死锁表。</p>
     *
     * @param ds  数据源
     * @param sql 写操作 SQL
     * @return 影响行数
     * @throws SQLException 执行失败时抛出
     */
    public int update(DataSource ds, String sql) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(config.getQueryTimeoutSeconds());
                int affected = stmt.executeUpdate(sql);
                conn.commit();
                return affected;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * 结果集映射函数式接口。
     *
     * @param <T> 映射结果类型
     */
    @FunctionalInterface
    public interface SqlResultMapper<T> {

        /**
         * 将结果集映射为业务对象。
         *
         * @param rs 结果集（调用方负责关闭）
         * @return 映射结果
         * @throws SQLException 读取失败时抛出
         */
        T map(ResultSet rs) throws SQLException;
    }
}