package com.realapex.client.trace;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨线程/异步传递的 Trace 上下文——核心可复制上下文对象。
 * <p>用于在异步场景（线程池、MQ、SSE 流、Agent 子任务）下透传业务上下文与追踪标识，
 * 解决 {@code ThreadLocal} 无法跨线程传递的问题：主线程在提交任务前
 * {@code copy()} 快照上下文，异步线程通过 {@link LLMTraceContext#wrap} 恢复后再消费。</p>
 *
 * <p>字段对应 {@code sys_llm_invoke_log} 表的业务上下文列：
 * <code>scene_type / session_id / sub_dir_id / user_id / trace_id / parent_log_id</code>。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * TraceContext ctx = TraceContext.builder()
 *         .sceneType("DB_ANALYSIS")
 *         .sessionId(sessionId)
 *         .userId("u-1001")
 *         .build();
 * completableFuture.supplyAsync(() -> {
 *     LLMTraceContext.set(ctx);   // 异步线程内恢复上下文
 *     return agent.analyze(dbConfig);
 * });
 * }</pre>
 */
@Data
@Builder
public class TraceContext {

    /** 全局 Trace ID（跨线程/异步任务传递的追踪标识），为空时由 SDK 自动生成 */
    private String traceId;

    /** 父 Log ID（Agent 拆解子任务 / 多轮 Prompt 树状追踪） */
    private String parentLogId;

    /** 场景类型：DB_ANALYSIS, FILE_ANALYSIS ... */
    private String sceneType;

    /** 会话 ID */
    private String sessionId;

    /** 隔离目录 ID（针对文件场景） */
    private String subDirId;

    /** 用户 ID */
    private String userId;

    /** 扩展属性（随上下文透传，不落库） */
    @Builder.Default
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 快照当前上下文，供异步提交时透传。
     *
     * @return 上下文的一个深拷贝副本（属性 Map 仅浅拷贝引用级）
     */
    public TraceContext copy() {
        return TraceContext.builder()
                .traceId(traceId)
                .parentLogId(parentLogId)
                .sceneType(sceneType)
                .sessionId(sessionId)
                .subDirId(subDirId)
                .userId(userId)
                .attributes(attributes)
                .build();
    }
}