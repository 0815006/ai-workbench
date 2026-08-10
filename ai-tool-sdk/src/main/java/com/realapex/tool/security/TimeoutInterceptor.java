package com.realapex.tool.security;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * 工具执行超时拦截器——使用 {@link CompletableFuture} 限制工具最大执行时间。
 * <p>优先级: 50</p>
 */
@Slf4j
public class TimeoutInterceptor implements ToolSecurityInterceptor {

    /** 默认超时（秒） */
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private final long timeoutSeconds;

    /**
     * 使用默认超时（30 秒）。
     */
    public TimeoutInterceptor() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * @param timeoutSeconds 超时秒数
     */
    public TimeoutInterceptor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public int priority() {
        return 50;
    }

    /**
     * 包装工具执行为带超时的异步调用。
     * <p>注意：此方法不直接在 before/after 中实现超时，而是提供工具方法
     * 供外部在 execute 调用前包装。</p>
     *
     * @param toolName 工具名称
     * @param task     工具执行任务
     * @param <T>      返回类型
     * @return 执行结果
     * @throws TimeoutException 超时时抛出
     */
    public <T> T executeWithTimeout(String toolName, Callable<T> task) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<T> future = executor.submit(task);
            try {
                T result = future.get(timeoutSeconds, TimeUnit.SECONDS);
                log.debug("工具 {} 在超时限制内完成 ({}s)", toolName, timeoutSeconds);
                return result;
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("工具 {} 执行超时 ({}s)", toolName, timeoutSeconds);
                throw new TimeoutException(
                        "工具 [" + toolName + "] 执行超时（" + timeoutSeconds + "s）");
            }
        }
    }

    /**
     * 获取配置的超时秒数。
     */
    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
