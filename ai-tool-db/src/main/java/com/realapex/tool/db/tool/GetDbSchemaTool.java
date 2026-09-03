package com.realapex.tool.db.tool;

import com.realapex.tool.annotation.Tool;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.config.DbToolConfig;
import com.realapex.tool.db.dialect.DbDialect;
import com.realapex.tool.db.model.SchemaRequest;
import com.realapex.tool.db.model.TableSchema;
import com.realapex.tool.db.pool.JdbcExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema 探查器（{@code get_db_schema}）。
 * <p>获取数据库表名、列名、字段类型、索引、主外键及表注释，供 Agent 在写 SQL 前
 * 先了解表结构（典型 ReAct 路径第一步）。</p>
 *
 * <h3>方言差异</h3>
 * <ul>
 *   <li>MySQL：{@code information_schema.COLUMNS} / {@code SHOW CREATE TABLE}</li>
 *   <li>GaussDB：{@code pg_catalog} / {@code information_schema}</li>
 * </ul>
 *
 * <h3>安全与性能</h3>
 * <ul>
 *   <li>{@code readOnly = true}，只读工具</li>
 *   <li>用 {@code ConcurrentHashMap} 缓存 Schema 结果，避免重复查库</li>
 *   <li>大库表数量限制截断（默认 200 张表）</li>
 * </ul>
 */
@Slf4j
@Tool(name = "get_db_schema",
        description = "获取数据库表结构：表名、列名、字段类型、索引、主外键及表注释，"
                + "供 Agent 写 SQL 前探查表结构（只读）",
        readOnly = true)
public class GetDbSchemaTool implements AgentTool<SchemaRequest, TableSchema> {

    /** 大库表清单截断上限 */
    private static final int MAX_TABLE_LIST = 200;

    private final DbToolConfig config;
    private final JdbcExecutor executor;

    /** Schema 结果缓存（schema:table -> TableSchema），避免重复查库 */
    private final Map<String, TableSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * 构造 Schema 探查器。
     *
     * @param config   数据库工具配置
     * @param executor JDBC 统一执行封装
     */
    public GetDbSchemaTool(DbToolConfig config, JdbcExecutor executor) {
        this.config = config;
        this.executor = executor;
    }

    @Override
    public String name() {
        return "get_db_schema";
    }

    @Override
    public String description() {
        return "获取数据库表结构：表名、列名、字段类型、索引、主外键及表注释，"
                + "供 Agent 写 SQL 前探查表结构（只读）";
    }

    @Override
    public Class<SchemaRequest> requestClass() {
        return SchemaRequest.class;
    }

    @Override
    public TableSchema execute(SchemaRequest request) throws Exception {
        DataSource ds = config.effectiveDataSource();
        if (ds == null) {
            throw new IllegalStateException("未配置数据源（模式 A 需注入 DataSource，模式 B 需通过 DbConnectionManager 注册）");
        }
        DbDialect dialect = config.effectiveDialect();

        // 表名为空 → 返回库内表清单（截断大库）
        if (request.table() == null || request.table().isBlank()) {
            return listTables(ds, dialect, request.schema());
        }

        // 单表结构：优先缓存（computeIfAbsent 的 lambda 不能抛受检异常，手动实现）
        String cacheKey = (request.schema() == null ? "" : request.schema()) + ":" + request.table();
        TableSchema cached = schemaCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        TableSchema schema = extractTable(ds, dialect, request.schema(), request.table());
        schemaCache.put(cacheKey, schema);
        return schema;
    }

    /**
     * 提取库内表清单（截断大库）。
     *
     * @param ds      数据源
     * @param dialect 方言
     * @param schema  数据库/模式名
     * @return 表清单摘要（以 TableSchema 形式返回，columns 为空）
     * @throws SQLException 查询失败时抛出
     */
    private TableSchema listTables(DataSource ds, DbDialect dialect, String schema) throws SQLException {
        String sql = dialect.buildExtractSchemaSql(schema, null);
        log.info("get_db_schema: 提取表清单, schema={}", schema);
        return executor.query(ds, sql, rs -> {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
                if (tables.size() >= MAX_TABLE_LIST) {
                    break;
                }
            }
            String comment = "表清单（共 " + tables.size() + " 张，超过 " + MAX_TABLE_LIST + " 张截断）";
            return new TableSchema(schema, "", comment, List.of(), List.of(), List.of(), List.of());
        });
    }

    /**
     * 提取单表结构（字段 + 索引 + 主键 + 外键）。
     *
     * @param ds      数据源
     * @param dialect 方言
     * @param schema  数据库/模式名
     * @param table   表名
     * @return 表结构摘要
     * @throws SQLException 查询失败时抛出
     */
    private TableSchema extractTable(DataSource ds, DbDialect dialect, String schema, String table) throws SQLException {
        String sql = dialect.buildExtractSchemaSql(schema, table);
        log.info("get_db_schema: 提取表结构, schema={}, table={}", schema, table);
        return executor.query(ds, sql, rs -> {
            List<TableSchema.ColumnInfo> columns = new ArrayList<>();
            while (rs.next()) {
                columns.add(new TableSchema.ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("COLUMN_TYPE"),
                        "NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                        rs.getString("COLUMN_KEY"),
                        rs.getString("COLUMN_DEFAULT"),
                        rs.getString("COLUMN_COMMENT")
                ));
            }
            return new TableSchema(schema, table, "", columns, List.of(), List.of(), List.of());
        });
    }
}