package com.realapex.tool.db.security;

import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.dialect.MySqlDialect;
import com.realapex.tool.db.dialect.TdsqlDialect;
import com.realapex.tool.db.model.UpdateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 写操作安全卡口单元测试：无条件 WHERE 阻断 / 高危 DDL 拦截 / 影响行数上限 / TDSQL 分片键。
 */
class SqlSafetyCheckerTest {

    @Test
    void allowInsert() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new MySqlDialect()).build());
        assertDoesNotThrow(() -> checker.validateUpdate(new UpdateRequest("INSERT INTO t(id) VALUES(1)")));
    }

    @Test
    void allowUpdateWithWhere() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new MySqlDialect()).build());
        assertDoesNotThrow(() -> checker.validateUpdate(new UpdateRequest("UPDATE t SET a=1 WHERE id=1")));
    }

    @Test
    void blockUpdateWithoutWhere() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new MySqlDialect()).build());
        assertThrows(SecurityException.class,
                () -> checker.validateUpdate(new UpdateRequest("UPDATE t SET a=1")));
    }

    @Test
    void blockDeleteWithoutWhere() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new MySqlDialect()).build());
        assertThrows(SecurityException.class,
                () -> checker.validateUpdate(new UpdateRequest("DELETE FROM t")));
    }

    @Test
    void blockDdl() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new MySqlDialect()).build());
        assertThrows(SecurityException.class,
                () -> checker.validateUpdate(new UpdateRequest("DROP TABLE t")));
        assertThrows(SecurityException.class,
                () -> checker.validateUpdate(new UpdateRequest("TRUNCATE TABLE t")));
        assertThrows(SecurityException.class,
                () -> checker.validateUpdate(new UpdateRequest("GRANT ALL ON t TO user1")));
    }

    @Test
    void blockMultiStatement() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new MySqlDialect()).build());
        assertThrows(SecurityException.class,
                () -> checker.validateUpdate(new UpdateRequest("UPDATE t SET a=1 WHERE id=1; DROP TABLE t")));
    }

    @Test
    void tdsqlShardKeyRequired() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new TdsqlDialect()).build());
        // 带 WHERE 但无分片键 → 阻断
        assertThrows(SecurityException.class,
                () -> checker.validateUpdate(new UpdateRequest("UPDATE t SET a=1 WHERE id=1")));
        // 带分片键 → 放行
        assertDoesNotThrow(() -> checker.validateUpdate(new UpdateRequest("UPDATE t SET a=1 WHERE user_id=1")));
    }

    @Test
    void affectedRowsLimit() {
        SqlSafetyChecker checker = new SqlSafetyChecker(DbToolConfig.builder()
                .dialect(new MySqlDialect())
                .maxAffectedRows(500)
                .build());
        assertDoesNotThrow(() -> checker.validateAffectedRows(100));
        assertThrows(SecurityException.class, () -> checker.validateAffectedRows(501));
    }
}