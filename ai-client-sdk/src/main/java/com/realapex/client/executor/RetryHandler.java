package com.realapex.client.executor;

import com.realapex.client.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 指数退避重试处理器（含随机抖动 Jitter）。
 * <p>基于虚拟线程 + 手动退避，不依赖第三方 HTTP 框架的 Interceptor。</p>
 */
@Slf4j
public class RetryHandler {

    private final int maxRetries;
    private final Duration baseDelay;
    /** 随机抖动上限（毫秒），防止惊群效应 */
    private static final long JITTER_MAX_MS = 1000;

    /**
     * @param maxRetries 最大重试次数
     * @param baseDelay  基础退避延迟
     */
    public RetryHandler(int maxRetries, Duration baseDelay) {
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
    }

    /**
     * 执行带重试的操作。
     * <p>仅对 429 (Rate Limit) 和 5xx (Server Error) 进行重试。
     * 退避策略：baseDelay × 2^attempt + random(0, 1000ms)（指数退避 + 随机抖动）。</p>
     *
     * @param operation 要执行的操作
     * @param <T>       返回值类型
     * @return 操作结果
     * @throws RateLimitException 重试耗尽后抛出
     */
    public <T> T executeWithRetry(Supplier<T> operation) {
        int attempt = 0;
        while (true) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                if (!shouldRetry(e) || attempt >= maxRetries) {
                    if (attempt >= maxRetries) {
                        throw new RateLimitException(
                                "重试 " + maxRetries + " 次后仍失败: " + e.getMessage(), e);
                    }
                    throw e;
                }
                attempt++;
                long baseDelayMs = baseDelay.toMillis() * (1L << (attempt - 1));
                long jitterMs = ThreadLocalRandom.current().nextLong(JITTER_MAX_MS);
                long delayMs = baseDelayMs + jitterMs;
                log.warn("请求失败（第 {} 次重试，{}ms 后重试，含 {}ms 抖动）: {}",
                        attempt, delayMs, jitterMs, e.getMessage());
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
    }

    /**
     * 判断是否应重试：429 或 5xx。
     * <p>优先检查异常类型，其次检查异常消息内容。</p>
     */
    private boolean shouldRetry(RuntimeException e) {
        // 1. 显式 RateLimitException 始终重试
        if (e instanceof RateLimitException) {
            return true;
        }

        // 2. 检查消息中是否包含可重试的 HTTP 状态码
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("429") || msg.contains("500") || msg.contains("502")
                || msg.contains("503") || msg.contains("504");
    }
}
