package com.realapex.tool.db.pool;

import com.zaxxer.hikari.HikariDataSource;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 托管数据源——工具包自建连接池的包装（模式 B）。
 * <p>记录最近访问时间（{@code lastAccessNanos}），供 {@link DbConnectionManager}
 * 的 TTL 驱逐调度器判断是否超时未访问并自动关闭销毁。</p>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>创建：{@link HikariDataSourceBuilder} 构建 HikariCP 连接池</li>
 *   <li>访问：每次借还连接时刷新 {@code lastAccessNanos}</li>
 *   <li>销毁：超过 {@code idleTtl} 未访问 → {@link #close()} 关闭并移除</li>
 * </ul>
 */
public final class ManagedDataSource implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final AtomicLong lastAccessNanos = new AtomicLong(System.nanoTime());

    /**
     * 构造托管数据源。
     *
     * @param dataSource HikariCP 连接池实例
     */
    public ManagedDataSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 获取底层 HikariCP 连接池。
     *
     * @return HikariDataSource
     */
    public HikariDataSource dataSource() {
        return dataSource;
    }

    /**
     * 刷新最近访问时间（每次借还连接时调用）。
     */
    public void touch() {
        lastAccessNanos.set(System.nanoTime());
    }

    /**
     * 距上次访问的毫秒数。
     *
     * @return 空闲毫秒数
     */
    public long idleMillis() {
        return (System.nanoTime() - lastAccessNanos.get()) / 1_000_000L;
    }

    /**
     * 关闭底层连接池（幂等，可重复调用）。
     */
    @Override
    public void close() {
        dataSource.close();
    }
}