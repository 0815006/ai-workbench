package com.realapex.tool.db.config;

import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.dialect.MySqlDialect;
import com.realapex.tool.db.tool.ExecuteUpdateTool;
import com.realapex.tool.db.tool.GetDbSchemaTool;
import com.realapex.tool.db.tool.ReadOnlyQueryTool;
import com.realapex.tool.db.tool.SlowLogFetcherTool;
import com.realapex.tool.db.tool.SqlExplainTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具工厂单元测试：5 个原子工具 + HITL 审批标记 + 只读拦截器。
 */
class DbToolFactoryTest {

    @Test
    void createFiveTools() {
        DbToolConfig config = DbToolConfig.builder()
                .dialect(new MySqlDialect())
                .build();
        List<AgentTool<?, ?>> tools = DbToolFactory.createDbTools(config);
        assertEquals(5, tools.size());

        assertTrue(tools.stream().anyMatch(t -> t instanceof GetDbSchemaTool));
        assertTrue(tools.stream().anyMatch(t -> t instanceof ReadOnlyQueryTool));
        assertTrue(tools.stream().anyMatch(t -> t instanceof SqlExplainTool));
        assertTrue(tools.stream().anyMatch(t -> t instanceof SlowLogFetcherTool));
        assertTrue(tools.stream().anyMatch(t -> t instanceof ExecuteUpdateTool));
    }

    @Test
    void toolNames() {
        DbToolConfig config = DbToolConfig.builder().build();
        List<AgentTool<?, ?>> tools = DbToolFactory.createDbTools(config);
        List<String> names = tools.stream().map(AgentTool::name).toList();
        assertEquals(List.of("get_db_schema", "readonly_query", "explain_sql",
                "fetch_slow_logs", "execute_update"), names);
    }

    @Test
    void executeUpdateRequiresApproval() {
        DbToolConfig config = DbToolConfig.builder().build();
        List<AgentTool<?, ?>> tools = DbToolFactory.createDbTools(config);
        AgentTool<?, ?> updateTool = tools.stream()
                .filter(t -> t.name().equals("execute_update"))
                .findFirst().orElseThrow();
        assertTrue(updateTool.requiresApproval(), "execute_update 必须触发 HITL 审批");

        // 其余 4 个只读工具无需审批
        tools.stream().filter(t -> !t.name().equals("execute_update"))
                .forEach(t -> assertFalse(t.requiresApproval(), t.name() + " 不应触发审批"));
    }

    @Test
    void createReadOnlyInterceptor() {
        assertNotNull(DbToolFactory.createReadOnlyInterceptor());
        assertEquals(5, DbToolFactory.createReadOnlyInterceptor().priority());
    }

    @Test
    void createWithDataSourceOverload() {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        List<AgentTool<?, ?>> tools = DbToolFactory.createDbTools(ds, new MySqlDialect());
        assertEquals(5, tools.size());
    }
}