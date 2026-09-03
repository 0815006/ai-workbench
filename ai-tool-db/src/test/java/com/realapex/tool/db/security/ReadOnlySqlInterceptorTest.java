package com.realapex.tool.db.security;

import com.realapex.tool.db.model.ExplainRequest;
import com.realapex.tool.db.model.QueryRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语法级只读校验单元测试：AST 白名单放行 / 写语句与多语句拦截。
 */
class ReadOnlySqlInterceptorTest {

    private final ReadOnlySqlInterceptor interceptor = new ReadOnlySqlInterceptor();

    @Test
    void priorityIs5() {
        assertEquals(5, interceptor.priority());
    }

    @Test
    void allowSelect() {
        assertDoesNotThrow(() -> interceptor.before("readonly_query",
                new QueryRequest("SELECT * FROM orders WHERE id=1", null)));
    }

    @Test
    void allowWithCte() {
        assertDoesNotThrow(() -> interceptor.before("readonly_query",
                new QueryRequest("WITH t AS (SELECT id FROM orders) SELECT * FROM t", null)));
    }

    @Test
    void allowExplainShowDescribe() {
        assertDoesNotThrow(() -> interceptor.before("explain_sql",
                new ExplainRequest("SELECT * FROM orders WHERE id=1")));
        assertDoesNotThrow(() -> interceptor.before("readonly_query",
                new QueryRequest("SHOW TABLES", null)));
        assertDoesNotThrow(() -> interceptor.before("readonly_query",
                new QueryRequest("DESCRIBE orders", null)));
    }

    @Test
    void blockUpdate() {
        assertThrows(SecurityException.class, () -> interceptor.before("readonly_query",
                new QueryRequest("UPDATE orders SET status=1 WHERE id=1", null)));
    }

    @Test
    void blockDelete() {
        assertThrows(SecurityException.class, () -> interceptor.before("readonly_query",
                new QueryRequest("DELETE FROM orders WHERE id=1", null)));
    }

    @Test
    void blockInsert() {
        assertThrows(SecurityException.class, () -> interceptor.before("readonly_query",
                new QueryRequest("INSERT INTO orders(id) VALUES(1)", null)));
    }

    @Test
    void blockDrop() {
        assertThrows(SecurityException.class, () -> interceptor.before("readonly_query",
                new QueryRequest("DROP TABLE orders", null)));
    }

    @Test
    void blockMultiStatement() {
        // 多语句（; 分隔）→ JSqlParser 解析失败 → 拦截
        assertThrows(SecurityException.class, () -> interceptor.before("readonly_query",
                new QueryRequest("SELECT * FROM orders; DROP TABLE orders", null)));
    }

    @Test
    void blockCommentInjection() {
        // 注释注入：SELECT 前缀后拼接恶意语句
        assertThrows(SecurityException.class, () -> interceptor.before("readonly_query",
                new QueryRequest("SELECT * FROM orders; -- comment\nDELETE FROM orders", null)));
    }
}