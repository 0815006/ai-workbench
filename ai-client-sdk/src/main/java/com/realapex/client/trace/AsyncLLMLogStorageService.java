package com.realapex.client.trace;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步落盘服务——负责将 {@link TraceRecord} 写入 {@code sys_llm_invoke_log} 表。
 * <p>核心设计：</p>
 * <ul>
 *   <li><b>预插入（同步）</b>：{@link #saveStart} 在调用开始前同步插入一条
 *       {@code RUNNING} 记录并立即返回 {@code log_id}，确保后续 UPDATE 有明确主键。</li>
 *   <li><b>结果更新（异步）</b>：{@link #saveEnd} 在调用结束后提交到线程池异步执行，
 *       不阻塞主调用路径（SSE 响应、Agent 循环）。</li>
 *   <li><b>数据源复用</b>：直接使用应用侧已有的 {@link DataSource}，不新建数据库连接。</li>
 * </ul>
 * <p>数据源接口为 JDK 标准 {@code javax.sql.DataSource}，因此本服务既不强制依赖
 * Spring，也不强制依赖 spring-jdbc —— 纯 Java 项目同样可用。</p>
 */
@Slf4j
public class AsyncLLMLogStorageService implements AutoCloseable {

    private final DataSource dataSource;
    private final String tableName;
    private final ExecutorService executor;

    /**
     * 构造异步落盘服务。
     *
     * @param dataSource 数据库数据源（复用应用侧），不能为空
     * @param tableName  日志表名
     * @param poolSize   异步落盘线程池核心线程数
     */
    public AsyncLLMLogStorageService(DataSource dataSource, String tableName, int poolSize) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.executor = Executors.newFixedThreadPool(Math.max(1, poolSize));
    }

    /**
     * 预插入一条日志并返回生成的 log_id。
     * <p>此时记录状态为 {@link TraceStatus#RUNNING}，request_payload 已写入，
     * 响应载荷留空，供后续 {@link #saveEnd} 更新。</p>
     *
     * @param record 待落盘的记录（含 trace_id、业务上下文、请求载荷）
     * @return 数据库生成的 log_id；写入失败时返回 null
     */
    public String saveStart(TraceRecord record) {
        String sql = new StringBuilder()
                .append("INSERT INTO ").append(tableName).append(" (")
                .append("trace_id, parent_log_id, scene_type, session_id, sub_dir_id, user_id, ")
                .append("status, provider, model_name, call_type, ")
                .append("request_payload, created_at, start_time) ")
                .append("VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)")
                .toString();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"log_id"})) {
            ps.setString(1, record.getTraceId());
            ps.setString(2, record.getParentLogId());
            ps.setString(3, record.getSceneType());
            ps.setString(4, record.getSessionId());
            ps.setString(5, record.getSubDirId());
            ps.setString(6, record.getUserId());
            ps.setString(7, TraceStatus.RUNNING.code());
            ps.setString(8, record.getProvider());
            ps.setString(9, record.getModelName());
            ps.setString(10, record.getCallType());
            ps.setString(11, record.getRequestPayload());
            ps.setObject(12, OffsetDateTime.now());
            ps.setObject(13, OffsetDateTime.now());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        String logId = rs.getString(1);
                        log.debug("trace 预插入成功: log_id={}, trace_id={}", logId, record.getTraceId());
                        return logId;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("trace 预插入失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 异步更新一条日志为最终状态（SUCCESS / FAILED）并附带响应载荷与统计。
     * <p>提交到线程池执行，不阻塞调用方。</p>
     *
     * @param record 含 log_id、最终状态、响应载荷、Token、耗时、错误信息的记录
     */
    public void saveEnd(TraceRecord record) {
        if (record.getLogId() == null) {
            log.warn("trace 更新跳过：log_id 为空");
            return;
        }
        executor.submit(() -> {
            String sql = new StringBuilder()
                    .append("UPDATE ").append(tableName).append(" SET ")
                    .append("status=?, prompt_tokens=?, completion_tokens=?, total_tokens=?, ")
                    .append("latency_ms=?, first_token_latency_ms=?, ")
                    .append("response_payload=?::jsonb, error_message=?, end_time=? ")
                    .append("WHERE log_id=?")
                    .toString();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.getStatus().code());
                ps.setInt(2, record.getPromptTokens());
                ps.setInt(3, record.getCompletionTokens());
                ps.setInt(4, record.getTotalTokens());
                ps.setLong(5, record.getLatencyMs());
                ps.setLong(6, record.getFirstTokenLatencyMs());
                ps.setString(7, record.getResponsePayload());
                ps.setString(8, record.getErrorMessage());
                ps.setObject(9, OffsetDateTime.now());
                ps.setObject(10, toUUID(record.getLogId()));
                ps.executeUpdate();
                log.debug("trace 更新完成: log_id={}, status={}", record.getLogId(), record.getStatus());
            } catch (Exception e) {
                log.warn("trace 更新失败: log_id={}, err={}", record.getLogId(), e.getMessage());
            }
        });
    }

    /**
     * 将字符串 log_id 转为数据库 UUID，格式不合法时回退为字符串（避免崩溃）。
     *
     * @param logId 字符串形式的 log_id
     * @return UUID 或原字符串
     */
    private Object toUUID(String logId) {
        try {
            return UUID.fromString(logId);
        } catch (IllegalArgumentException e) {
            return logId;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}