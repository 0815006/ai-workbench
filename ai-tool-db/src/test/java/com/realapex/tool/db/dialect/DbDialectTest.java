package com.realapex.tool.db.dialect;

import com.realapex.tool.db.model.SlowLogFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方言归一化单元测试：EXPLAIN 语法、LIMIT 改写、Schema 提取、慢查询 SQL、标识符转义。
 */
class DbDialectTest {

    @Test
    void mysqlExplain() {
        DbDialect dialect = new MySqlDialect();
        assertEquals("EXPLAIN SELECT * FROM t", dialect.buildExplainSql("SELECT * FROM t"));
    }

    @Test
    void gaussExplain() {
        DbDialect dialect = new GaussDbDialect();
        assertEquals("EXPLAIN (ANALYZE, COSTS) SELECT * FROM t",
                dialect.buildExplainSql("SELECT * FROM t"));
    }

    @Test
    void mysqlApplyLimit() {
        DbDialect dialect = new MySqlDialect();
        assertEquals("SELECT * FROM t LIMIT 100", dialect.applyLimit("SELECT * FROM t", 100));
        // 已带 LIMIT 不重复注入
        assertEquals("SELECT * FROM t LIMIT 10", dialect.applyLimit("SELECT * FROM t LIMIT 10", 100));
        // 末尾分号处理
        assertEquals("SELECT * FROM t LIMIT 100", dialect.applyLimit("SELECT * FROM t;", 100));
    }

    @Test
    void gaussApplyLimit() {
        DbDialect dialect = new GaussDbDialect();
        assertEquals("SELECT * FROM t LIMIT 100", dialect.applyLimit("SELECT * FROM t", 100));
        // FETCH FIRST 已存在不重复注入
        assertEquals("SELECT * FROM t FETCH FIRST 5 ROWS ONLY",
                dialect.applyLimit("SELECT * FROM t FETCH FIRST 5 ROWS ONLY", 100));
    }

    @Test
    void mysqlSchemaSql() {
        DbDialect dialect = new MySqlDialect();
        String tableSql = dialect.buildExtractSchemaSql("demo", "orders");
        assertTrue(tableSql.contains("information_schema.COLUMNS"));
        assertTrue(tableSql.contains("TABLE_NAME = 'orders'"));
        String listSql = dialect.buildExtractSchemaSql("demo", null);
        assertTrue(listSql.contains("information_schema.TABLES"));
        assertTrue(listSql.contains("TABLE_SCHEMA = 'demo'"));
    }

    @Test
    void gaussSchemaSql() {
        DbDialect dialect = new GaussDbDialect();
        String tableSql = dialect.buildExtractSchemaSql("public", "orders");
        assertTrue(tableSql.contains("information_schema.columns"));
        assertTrue(tableSql.contains("table_name = 'orders'"));
        String listSql = dialect.buildExtractSchemaSql("public", null);
        assertTrue(listSql.contains("pg_catalog.pg_tables"));
        assertTrue(listSql.contains("schemaname = 'public'"));
    }

    @Test
    void mysqlSlowLogQuery() {
        DbDialect dialect = new MySqlDialect();
        SlowLogFilter filter = new SlowLogFilter("2026-09-01T00:00:00", "2026-09-02T00:00:00",
                1000L, "SELECT", 10);
        String sql = dialect.buildSlowLogQuery(filter);
        assertTrue(sql.contains("mysql.slow_log"));
        assertTrue(sql.contains("start_time >= '2026-09-01T00:00:00'"));
        assertTrue(sql.contains("query_time >= 1.0"));
        assertTrue(sql.contains("sql_text LIKE '%SELECT%'"));
        assertTrue(sql.contains("LIMIT 10"));
    }

    @Test
    void gaussSlowLogQuery() {
        DbDialect dialect = new GaussDbDialect();
        SlowLogFilter filter = new SlowLogFilter(null, null, 500L, null, 5);
        String sql = dialect.buildSlowLogQuery(filter);
        assertTrue(sql.contains("dbe_perf.statement_history"));
        assertTrue(sql.contains("duration >= 0.5"));
        assertTrue(sql.contains("LIMIT 5"));
    }

    @Test
    void quoteIdentifier() {
        assertEquals("`col`", new MySqlDialect().quoteIdentifier("col"));
        assertEquals("\"col\"", new GaussDbDialect().quoteIdentifier("col"));
    }

    @Test
    void dialectFactoryDetect() {
        assertInstanceOf(MySqlDialect.class, DbDialectFactory.detect("jdbc:mysql://localhost:3306/demo"));
        assertInstanceOf(TdsqlDialect.class, DbDialectFactory.detect("jdbc:tdsql://10.0.0.1:3306/demo"));
        assertInstanceOf(GaussDbDialect.class, DbDialectFactory.detect("jdbc:opengauss://localhost:5432/demo"));
        assertInstanceOf(GaussDbDialect.class, DbDialectFactory.detect("jdbc:postgresql://localhost:5432/demo"));
        assertInstanceOf(MySqlDialect.class, DbDialectFactory.detect(null));
    }

    @Test
    void dialectFactoryCreate() {
        assertInstanceOf(MySqlDialect.class, DbDialectFactory.create("mysql"));
        assertInstanceOf(TdsqlDialect.class, DbDialectFactory.create("tdsql"));
        assertInstanceOf(GaussDbDialect.class, DbDialectFactory.create("gaussdb"));
        assertThrows(IllegalArgumentException.class, () -> DbDialectFactory.create("oracle"));
    }

    @Test
    void mysqlValidateSqlSafety() {
        DbDialect dialect = new MySqlDialect();
        // 无条件 UPDATE 阻断
        assertThrows(SecurityException.class, () -> dialect.validateSqlSafety("UPDATE t SET a=1"));
        // 带 WHERE 放行
        assertDoesNotThrow(() -> dialect.validateSqlSafety("UPDATE t SET a=1 WHERE id=1"));
        // 高危 DDL 拦截
        assertThrows(SecurityException.class, () -> dialect.validateSqlSafety("DROP TABLE t"));
        assertThrows(SecurityException.class, () -> dialect.validateSqlSafety("TRUNCATE TABLE t"));
    }

    @Test
    void tdsqlValidateShardKey() {
        DbDialect dialect = new TdsqlDialect();
        // 无条件 UPDATE 阻断（继承 MySQL 校验）
        assertThrows(SecurityException.class, () -> dialect.validateSqlSafety("UPDATE t SET a=1"));
        // 带 WHERE 但无分片键 → 阻断（防广播全分片）
        assertThrows(SecurityException.class,
                () -> dialect.validateSqlSafety("UPDATE t SET a=1 WHERE id=1"));
        // 带分片键 → 放行
        assertDoesNotThrow(() -> dialect.validateSqlSafety("UPDATE t SET a=1 WHERE user_id=1"));
        assertDoesNotThrow(() -> dialect.validateSqlSafety("DELETE FROM t WHERE shard_key=1"));
    }
}