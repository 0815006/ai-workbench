package com.realapex.tool.db.dialect;

import lombok.extern.slf4j.Slf4j;

/**
 * 方言工厂——按 JDBC URL 自动探测或按名称显式创建 {@link DbDialect}。
 * <p>探测优先级：显式 {@code dialect} 名称 > JDBC URL 关键字匹配 > 默认 MySQL。</p>
 *
 * <h3>探测规则</h3>
 * <ul>
 *   <li>{@code jdbc:mysql://} → {@link MySqlDialect}</li>
 *   <li>{@code jdbc:tdsql://} / {@code jdbc:mysql://...tdsql...} → {@link TdsqlDialect}</li>
 *   <li>{@code jdbc:opengauss://} / {@code jdbc:gaussdb://} / {@code jdbc:postgresql://} → {@link GaussDbDialect}</li>
 * </ul>
 */
@Slf4j
public final class DbDialectFactory {

    private DbDialectFactory() {
    }

    /**
     * 按 JDBC URL 自动探测方言。
     *
     * @param jdbcUrl JDBC URL（可空，空则默认 MySQL）
     * @return 探测到的方言实例
     */
    public static DbDialect detect(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new MySqlDialect();
        }
        String url = jdbcUrl.toLowerCase();
        if (url.contains("tdsql")) {
            return new TdsqlDialect();
        }
        if (url.contains("opengauss") || url.contains("gaussdb") || url.contains("postgresql")) {
            return new GaussDbDialect();
        }
        if (url.contains("mysql")) {
            return new MySqlDialect();
        }
        log.warn("无法识别的 JDBC URL [{}]，回退默认 MySQL 方言", jdbcUrl);
        return new MySqlDialect();
    }

    /**
     * 按名称显式创建方言。
     *
     * @param name 方言名称（mysql / tdsql / gaussdb，大小写不敏感）
     * @return 方言实例
     * @throws IllegalArgumentException 未知方言名称时抛出
     */
    public static DbDialect create(String name) {
        if (name == null || name.isBlank()) {
            return new MySqlDialect();
        }
        return switch (name.trim().toLowerCase()) {
            case "mysql" -> new MySqlDialect();
            case "tdsql" -> new TdsqlDialect();
            case "gaussdb", "opengauss", "postgresql" -> new GaussDbDialect();
            default -> throw new IllegalArgumentException("未知数据库方言: " + name);
        };
    }
}