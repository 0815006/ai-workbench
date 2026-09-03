package com.realapex.client.trace;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Auto-DDL 兜底建表组件——为未启用 Flyway 的边缘应用自动建表。
 * <p>根据 PRD 原则：</p>
 * <ul>
 *   <li><b>优先交给 Flyway</b>：若应用侧启用了 Flyway（classpath 存在 Flyway 迁移脚本），
 *       本组件自动跳过，绝不重复建表，避免锁竞争与版本冲突。</li>
 *   <li><b>兜底自建</b>：未启用 Flyway 且 {@code ai.client.trace.auto-ddl=true} 时，
 *       读取 SDK 固化的 DDL 脚本（{@code classpath:db/sdk-migration/V1.0.0_1__create_sys_llm_invoke_log.sql}）
 *       用原生 JDBC 幂等执行（脚本本身为 {@code CREATE TABLE IF NOT EXISTS}）。</li>
 * </ul>
 */
@Slf4j
public class LLMTraceTableAutoInitializer {

    /** 固化 DDL 在 classpath 中的位置（与 Flyway 目录保持一致，双保险） */
    public static final String DDL_CLASSPATH = "db/sdk-migration/V1.0.0_1__create_sys_llm_invoke_log.sql";

    private final DataSource dataSource;
    private final String tableName;

    /**
     * 构造 Auto-DDL 初始化器。
     *
     * @param dataSource 数据库数据源
     * @param tableName  目标表名
     */
    public LLMTraceTableAutoInitializer(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
    }

    /**
     * 执行兜底建表逻辑。若已开启 Flyway 则静默跳过。
     */
    public void initializeIfNeeded() {
        // 1. Flyway 已接管时跳过（避免双建表/锁竞争）
        if (isFlywayActive()) {
            log.info("检测到 Flyway 已接管数据库迁移，跳过 SDK Auto-DDL 兜底建表");
            return;
        }
        // 2. 已存在目标表则跳过
        try {
            if (tableExists()) {
                log.debug("表 {} 已存在，跳过 Auto-DDL", tableName);
                return;
            }
        } catch (Exception e) {
            log.warn("检查表 {} 是否存在失败: {}", tableName, e.getMessage());
        }
        // 3. 执行固化 DDL
        try {
            String ddl = loadDdl();
            try (Connection conn = dataSource.getConnection();
                 Statement st = conn.createStatement()) {
                // 脚本包含多条语句，逐条以分号分割执行
                for (String statement : ddl.split(";")) {
                    String sql = statement.trim();
                    if (!sql.isEmpty()) {
                        st.execute(sql);
                    }
                }
                log.info("Auto-DDL 兜底建表完成: {}", tableName);
            }
        } catch (Exception e) {
            log.warn("Auto-DDL 兜底建表失败: {}", e.getMessage());
        }
    }

    /**
     * 是否已启用 Flyway（classpath 存在 Flyway 的迁移脚本或历史表）。
     */
    private boolean isFlywayActive() {
        // 简单探测：classpath 是否存在 Flyway 包，且数据库存在 flyway_schema_history 表
        try {
            boolean flywayOnClasspath;
            try {
                Class.forName("org.flywaydb.core.Flyway");
                flywayOnClasspath = true;
            } catch (ClassNotFoundException e) {
                flywayOnClasspath = false;
            }
            if (!flywayOnClasspath) {
                return false;
            }
            try (Connection conn = dataSource.getConnection();
                 ResultSet rs = conn.getMetaData().getTables(null, null, "flyway_schema_history", null)) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tableExists() throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private String loadDdl() throws IOException {
        try (InputStream in = LLMTraceTableAutoInitializer.class.getClassLoader().getResourceAsStream(DDL_CLASSPATH)) {
            if (in == null) {
                throw new IOException("未找到固化的 DDL 脚本: " + DDL_CLASSPATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}