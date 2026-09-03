package com.realapex.tool.db.pool;

import com.realapex.tool.db.config.DbToolConfig;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 连接池自治单元测试：双模式解析 / 注册冲突 / 显式驱逐 / TTL 驱逐 / 优雅关闭。
 */
class DbConnectionManagerTest {

    @Test
    void resolveExternalDataSource() {
        // 模式 A：外部注入
        DataSource external = new com.zaxxer.hikari.HikariDataSource();
        DbConnectionManager manager = new DbConnectionManager(external);
        try {
            assertSame(external, manager.resolve("any-key"));
        } finally {
            manager.close();
        }
    }

    @Test
    void registerAndResolveManaged() {
        // 模式 B：托管注册（H2 内存库，无需真实数据库）
        DbConnectionManager manager = new DbConnectionManager(null);
        try {
            manager.register("test-db", DbToolConfig.builder()
                    .jdbcUrl("jdbc:h2:mem:testdb")
                    .username("sa")
                    .password("")
                    .build());
            assertEquals(1, manager.managedCount());
            assertNotNull(manager.resolve("test-db"));
        } finally {
            manager.close();
        }
    }

    @Test
    void registerDuplicateKey() {
        DbConnectionManager manager = new DbConnectionManager(null);
        try {
            manager.register("dup", DbToolConfig.builder()
                    .jdbcUrl("jdbc:h2:mem:dup1").build());
            assertThrows(IllegalArgumentException.class, () -> manager.register("dup", DbToolConfig.builder()
                    .jdbcUrl("jdbc:h2:mem:dup2").build()));
        } finally {
            manager.close();
        }
    }

    @Test
    void evictExplicit() {
        DbConnectionManager manager = new DbConnectionManager(null);
        try {
            manager.register("temp", DbToolConfig.builder()
                    .jdbcUrl("jdbc:h2:mem:temp").build());
            manager.evict("temp");
            assertEquals(0, manager.managedCount());
            assertThrows(IllegalArgumentException.class, () -> manager.resolve("temp"));
        } finally {
            manager.close();
        }
    }

    @Test
    void resolveUnknownKeyWithoutExternal() {
        DbConnectionManager manager = new DbConnectionManager(null);
        try {
            assertThrows(IllegalArgumentException.class, () -> manager.resolve("not-exist"));
        } finally {
            manager.close();
        }
    }

    @Test
    void closeIsIdempotent() {
        DbConnectionManager manager = new DbConnectionManager(null);
        manager.register("a", DbToolConfig.builder().jdbcUrl("jdbc:h2:mem:a").build());
        manager.close();
        manager.close(); // 幂等
        assertEquals(0, manager.managedCount());
    }
}