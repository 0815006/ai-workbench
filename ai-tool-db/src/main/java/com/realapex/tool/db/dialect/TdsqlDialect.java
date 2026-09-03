package com.realapex.tool.db.dialect;

import com.realapex.tool.db.model.SlowLogFilter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * TDSQL 方言实现——继承 {@link MySqlDialect}，兼容 MySQL 协议。
 * <p>在 MySQL 语义基础上增强分片键校验：TDSQL 分布式表（shard 表）的
 * UPDATE/DELETE 必须携带分片键（Shard Key）条件，防止广播到全部分片。</p>
 *
 * <h3>连接池注意点</h3>
 * <ul>
 *   <li>TProxy 网关对长连接空闲断开敏感：{@code idleTimeout} 建议 1-2 分钟</li>
 *   <li>建议开启 {@code keepaliveTime}（如 30s）主动保活</li>
 * </ul>
 */
@Slf4j
public class TdsqlDialect extends MySqlDialect {

    /** 常见分片键命名（可扩展） */
    private static final List<Pattern> SHARD_KEY_PATTERNS = List.of(
            Pattern.compile("\\b(shard_key|shardkey|shard_id|shardid|tenant_id|tenantid|user_id|userid|order_id|orderid)\\b", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "tdsql";
    }

    @Override
    public String buildSlowLogQuery(SlowLogFilter filter) {
        // TDSQL 分片慢日志聚合视图（兼容 MySQL 字段命名）
        StringBuilder sb = new StringBuilder("SELECT start_time, query_time, rows_examined, rows_sent, db, sql_text FROM mysql.slow_log WHERE 1=1");
        for (String condition : buildSlowLogConditions(filter)) {
            sb.append(" AND ").append(condition);
        }
        int limit = filter.limit() != null ? filter.limit() : 20;
        sb.append(" ORDER BY start_time DESC LIMIT ").append(Math.max(1, limit));
        return sb.toString();
    }

    @Override
    public void validateSqlSafety(String sql) {
        // 先执行 MySQL 基础校验（高危 DDL/DCL + 无条件 WHERE）
        super.validateSqlSafety(sql);

        // TDSQL 增强：UPDATE/DELETE 必须携带分片键条件，防广播全分片
        String upper = sql.toUpperCase();
        if (upper.contains("UPDATE") || upper.contains("DELETE")) {
            boolean hasShardKey = SHARD_KEY_PATTERNS.stream()
                    .anyMatch(p -> p.matcher(sql).find());
            if (!hasShardKey) {
                throw new SecurityException("TDSQL 分布式表 UPDATE/DELETE 必须携带分片键（Shard Key）条件，防止广播全分片");
            }
        }
    }
}