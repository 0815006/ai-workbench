package com.realapex.tool.db.pool;

import com.realapex.tool.db.config.DbToolConfig;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 动态多数据源管理器——连接管理的唯一入口。
 * <p>工具不直接触碰 {@code Connection} 获取逻辑，统一通过本类解析数据源。
 * 核心职责：数据源注册/解析、连接借还、TTL/LRU 驱逐、优雅关闭。</p>
 *
 * <h3>双模式接入</h3>
 * <ul>
 *   <li><b>模式 A（外部注入，推荐）</b>：{@code externalDataSource} 由应用层注入，
 *       生命周期不归本工具包管理，{@link #resolve} 直接返回</li>
 *   <li><b>模式 B（工具包托管）</b>：{@link #register} 动态注册，内部构建 HikariCP 连接池，
 *       超过 {@code idleTtl} 未访问自动关闭销毁（防多租户连接数/OOM 爆炸）</li>
 * </ul>
 *
 * <h3>TTL 驱逐</h3>
 * <p>单线程 daemon 调度器周期扫描 {@code managedPool}，超过 {@code idleTtlMinutes}
 * 未访问的托管数据源自动 {@code close()} 并移除。零额外依赖（ConcurrentHashMap +
 * ScheduledExecutorService），契合工程最小依赖原则。</p>
 */
@Slf4j
public final class DbConnectionManager implements AutoCloseable {

    /** 默认 TTL 驱逐扫描周期：1 分钟 */
    private static final long EVICTION_INTERVAL_MINUTES = 1;

    /** 模式 A：应用层注入的外部 DataSource（生命周期不归本工具包管理） */
    private final DataSource externalDataSource;

    /** 模式 B：工具包托管的动态数据源缓存（key -> 托管数据源） */
    private final Map<String, ManagedDataSource> managedPool = new ConcurrentHashMap<>();

    /** TTL 驱逐调度器（单线程 daemon） */
    private final ScheduledExecutorService evictionExecutor;

    /** 托管数据源空闲驱逐 TTL（分钟） */
    private final long idleTtlMinutes;

    /**
     * 构造连接管理器（仅模式 A：外部注入）。
     *
     * @param externalDataSource 应用层注入的 DataSource
     */
    public DbConnectionManager(DataSource externalDataSource) {
        this(externalDataSource, DbToolConfig.DEFAULT_IDLE_TTL_MINUTES);
    }

    /**
     * 构造连接管理器（双模式）。
     *
     * @param externalDataSource 应用层注入的 DataSource（可为 null）
     * @param idleTtlMinutes     托管数据源空闲驱逐 TTL（分钟）
     */
    public DbConnectionManager(DataSource externalDataSource, long idleTtlMinutes) {
        this.externalDataSource = externalDataSource;
        this.idleTtlMinutes = idleTtlMinutes > 0 ? idleTtlMinutes : DbToolConfig.DEFAULT_IDLE_TTL_MINUTES;
        this.evictionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-tool-db-eviction");
            t.setDaemon(true);
            return t;
        });
        this.evictionExecutor.scheduleWithFixedDelay(this::evictIdle,
                EVICTION_INTERVAL_MINUTES, EVICTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 按 key 解析数据源：优先 managedPool，其次 externalDataSource。
     *
     * @param datasourceKey 数据源 key（模式 B 注册的 key；模式 A 可传任意值）
     * @return 解析到的 DataSource
     * @throws IllegalArgumentException 未找到对应数据源时抛出
     */
    public DataSource resolve(String datasourceKey) {
        if (datasourceKey != null && !datasourceKey.isBlank()) {
            ManagedDataSource managed = managedPool.get(datasourceKey);
            if (managed != null) {
                managed.touch();
                return managed.dataSource();
            }
        }
        if (externalDataSource != null) {
            return externalDataSource;
        }
        throw new IllegalArgumentException("未找到数据源: key=" + datasourceKey
                + "，且未配置外部 DataSource（模式 A）");
    }

    /**
     * 注册工具包托管数据源（模式 B），内部构建 HikariCP 连接池。
     *
     * @param key    数据源唯一 key（如 "test-db"）
     * @param config 数据库工具配置（三要素 + 连接池参数）
     * @throws IllegalArgumentException key 已存在或 jdbcUrl 为空时抛出
     */
    public void register(String key, DbToolConfig config) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("托管数据源 key 不能为空");
        }
        if (managedPool.containsKey(key)) {
            throw new IllegalArgumentException("托管数据源已存在: " + key);
        }
        ManagedDataSource managed = new ManagedDataSource(HikariDataSourceBuilder.build(config));
        managedPool.put(key, managed);
        log.info("注册托管数据源: key={}, url={}", key, config.getJdbcUrl());
    }

    /**
     * 显式注销并关闭指定数据源（幂等）。
     *
     * @param key 数据源 key
     */
    public void evict(String key) {
        ManagedDataSource removed = managedPool.remove(key);
        if (removed != null) {
            removed.close();
            log.info("注销托管数据源: key={}", key);
        }
    }

    /**
     * 当前托管数据源数量。
     *
     * @return 托管数据源数
     */
    public int managedCount() {
        return managedPool.size();
    }

    /**
     * 优雅关闭全部托管连接池与驱逐调度器。
     */
    @Override
    public void close() {
        evictionExecutor.shutdownNow();
        for (Map.Entry<String, ManagedDataSource> entry : managedPool.entrySet()) {
            entry.getValue().close();
            log.info("关闭托管数据源: key={}", entry.getKey());
        }
        managedPool.clear();
    }

    /**
     * TTL 驱逐扫描：超过 idleTtl 未访问的托管数据源自动关闭销毁。
     */
    private void evictIdle() {
        long ttlMillis = idleTtlMinutes * 60_000L;
        for (Map.Entry<String, ManagedDataSource> entry : managedPool.entrySet()) {
            ManagedDataSource managed = entry.getValue();
            if (managed.idleMillis() > ttlMillis) {
                managedPool.remove(entry.getKey());
                managed.close();
                log.info("TTL 驱逐托管数据源: key={}, idle={}ms > ttl={}ms",
                        entry.getKey(), managed.idleMillis(), ttlMillis);
            }
        }
    }
}