package com.realapex.client.trace;

import java.util.Optional;
import java.util.UUID;

/**
 * 线程级 Trace 上下文持有者——基于 {@link ThreadLocal} 的当前线程上下文存储。
 * <p>业务方在进入场景（Controller/Service 入口）时调用 {@link #set(TraceContext)}，
 * SDK 内部的 Trace 拦截器会自动从当前线程读取场景信息并落盘。</p>
 *
 * <h3>异步场景透传（关键）</h3>
 * <p>{@code ThreadLocal} 无法自动跨线程传递。当主线程把任务提交给线程池 / MQ /
 * SSE 异步线程时，需按以下两步手动透传：</p>
 * <pre>{@code
 * // 主线程：提交前捕获快照
 * TraceContext ctx = LLMTraceContext.get();
 * CompletableFuture.supplyAsync(() -> {
 *     // 异步线程：恢复上下文后再执行
 *     return LLMTraceContext.wrap(ctx, () -> agent.analyze(dbConfig));
 * });
 * }</pre>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>入口处 {@link #set(TraceContext)} 建立上下文</li>
 *   <li>方法结束（finally）处 {@link #clear()} 清理，防止线程池线程上下文泄漏</li>
 * </ul>
 */
public final class LLMTraceContext {

    private LLMTraceContext() {
    }

    private static final ThreadLocal<TraceContext> HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的 Trace 上下文。
     *
     * @param ctx Trace 上下文，可为 null（等价于 clear）
     */
    public static void set(TraceContext ctx) {
        if (ctx == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(ctx);
        }
    }

    /**
     * 获取当前线程的 Trace 上下文（可能为 null）。
     *
     * @return 当前上下文，未设置时返回 null
     */
    public static TraceContext get() {
        return HOLDER.get();
    }

    /**
     * 获取当前线程的 Trace 上下文，不存在时返回可选空值。
     *
     * @return 包裹当前上下文的 Optional
     */
    public static Optional<TraceContext> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * 清理当前线程的 Trace 上下文（线程池复用场景务必调用，防泄漏）。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 生成一个新的全局 Trace ID。
     *
     * @return UUID 字符串
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 在异步线程中执行任务并透传主线程上下文。
     * <p>实现原理：在目标线程内 {@code set(ctx)} 恢复上下文，执行完毕后清理，
     * 防止线程池线程将上下文泄漏给后续任务。</p>
     *
     * @param ctx  待透传的上下文（通常来自主线程 {@link #get()} 快照）
     * @param task 需要在当前（异步）线程执行的任务
     * @param <R>  任务返回类型
     * @return 任务执行结果
     */
    public static <R> R wrap(TraceContext ctx, Task<R> task) {
        TraceContext previous = HOLDER.get();
        HOLDER.set(ctx);
        try {
            return task.execute();
        } finally {
            if (previous != null) {
                HOLDER.set(previous);
            } else {
                HOLDER.remove();
            }
        }
    }

    /**
     * 在异步线程中执行任务并透传上下文（无返回值版本）。
     *
     * @param ctx  待透传的上下文
     * @param task 任务
     */
    public static void wrap(TraceContext ctx, Runnable task) {
        wrap(ctx, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 可执行任务接口（支持受检异常，交由调用方处理）。
     *
     * @param <R> 返回类型
     */
    @FunctionalInterface
    public interface Task<R> {
        /**
         * 执行任务。
         *
         * @return 结果
         * @throws RuntimeException 允许抛运行时异常
         */
        R execute();
    }
}