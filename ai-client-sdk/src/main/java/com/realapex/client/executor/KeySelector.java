package com.realapex.client.executor;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API Key 轮询选择器，含故障隔离（黑名单机制）。
 * <p>线程安全，支持多 Key Round-Robin 轮询，401/402 自动隔离。</p>
 */
@Slf4j
public class KeySelector {

    private final List<String> keys;
    private final AtomicInteger index;
    private final Map<String, Instant> blacklist;
    private final long blacklistDurationSeconds;

    /**
     * @param keys                   API Key 列表
     * @param blacklistDurationSeconds 黑名单隔离秒数
     */
    public KeySelector(List<String> keys, long blacklistDurationSeconds) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("apiKeys must not be null or empty");
        }
        this.keys = keys;
        this.index = new AtomicInteger(0);
        this.blacklist = new ConcurrentHashMap<>();
        this.blacklistDurationSeconds = blacklistDurationSeconds;
    }

    /**
     * 获取下一个可用 Key（Round-Robin，跳过黑名单中的 Key）。
     *
     * @return 可用的 API Key
     * @throws IllegalStateException 所有 Key 均被隔离时抛出
     */
    public String nextKey() {
        cleanupBlacklist();

        int size = keys.size();
        for (int i = 0; i < size; i++) {
            int idx = index.getAndUpdate(v -> (v + 1) % size);
            String key = keys.get(idx);
            if (!isBlacklisted(key)) {
                return key;
            }
        }

        throw new IllegalStateException("所有 API Key 均已被隔离，请检查 Key 有效性");
    }

    /**
     * 将指定 Key 加入黑名单（如遇 401/402）。
     *
     * @param key 要隔离的 API Key
     */
    public void blacklist(String key) {
        blacklist.put(key, Instant.now());
        log.warn("API Key {} 已被隔离 {} 秒", maskKey(key), blacklistDurationSeconds);
    }

    /**
     * 判断 Key 是否在黑名单中。
     */
    private boolean isBlacklisted(String key) {
        Instant blacklistedAt = blacklist.get(key);
        if (blacklistedAt == null) {
            return false;
        }
        if (Instant.now().isAfter(blacklistedAt.plusSeconds(blacklistDurationSeconds))) {
            blacklist.remove(key);
            log.info("API Key {} 隔离期满，已恢复", maskKey(key));
            return false;
        }
        return true;
    }

    /**
     * 清理过期黑名单条目。
     */
    private void cleanupBlacklist() {
        Instant cutoff = Instant.now().minusSeconds(blacklistDurationSeconds);
        blacklist.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    /**
     * 脱敏显示 Key（只保留前6后4位）。
     */
    private String maskKey(String key) {
        if (key.length() <= 10) {
            return key.substring(0, 3) + "***";
        }
        return key.substring(0, 6) + "***" + key.substring(key.length() - 4);
    }
}
