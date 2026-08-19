package io.github.sombreknight.feather.dialect;

import io.github.sombreknight.feather.exception.FeatherDaoException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 方言注册表：产品名探测、别名查找、默认方言。
 *
 * <p>自动探测通过 {@link DataSource#getConnection()} 读取
 * {@code DatabaseMetaData.getDatabaseProductName()}，按关键字归入对应方言族；未识别则回退
 * {@link DefaultDialect}（最小引用 + LIMIT/OFFSET，绝大多数场景仍可用）。</p>
 *
 * @author sombreknight
 */
public final class DialectRegistry {

    private static final DefaultDialect DEFAULT = new DefaultDialect();
    private static final MySqlDialect MYSQL = new MySqlDialect();
    private static final PostgresDialect POSTGRES = new PostgresDialect();
    private static final SqlServerDialect SQL_SERVER = new SqlServerDialect();
    private static final OracleDialect ORACLE = new OracleDialect();
    private static final SqliteDialect SQLITE = new SqliteDialect();
    private static final H2Dialect H2 = new H2Dialect();
    private static final DmDialect DM = new DmDialect();

    /** 配置别名 → 方言（用于 feather.orm.dialect 显式指定） */
    private static final Map<String, SqlDialect> BY_ALIAS = new LinkedHashMap<>();

    static {
        BY_ALIAS.put("default", DEFAULT);
        BY_ALIAS.put("mysql", MYSQL);
        BY_ALIAS.put("mariadb", MYSQL);
        BY_ALIAS.put("tidb", MYSQL);
        BY_ALIAS.put("oceanbase", MYSQL);
        BY_ALIAS.put("postgres", POSTGRES);
        BY_ALIAS.put("postgresql", POSTGRES);
        BY_ALIAS.put("opengauss", POSTGRES);
        BY_ALIAS.put("kingbase", POSTGRES);
        BY_ALIAS.put("sqlserver", SQL_SERVER);
        BY_ALIAS.put("mssql", SQL_SERVER);
        BY_ALIAS.put("oracle", ORACLE);
        BY_ALIAS.put("sqlite", SQLITE);
        BY_ALIAS.put("h2", H2);
        BY_ALIAS.put("hsqldb", H2);
        BY_ALIAS.put("dm", DM);
        BY_ALIAS.put("dameng", DM);
    }

    private DialectRegistry() {
    }

    public static SqlDialect defaultDialect() {
        return DEFAULT;
    }

    /**
     * 从数据源自动探测方言（读一次元数据，应用启动时调用一次）
     */
    public static SqlDialect detect(DataSource dataSource) {
        if (dataSource == null) {
            return DEFAULT;
        }
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return fromProductName(productName);
        } catch (SQLException e) {
            throw new FeatherDaoException("SQL 方言自动探测失败（读取 DatabaseMetaData 异常）", e);
        }
    }

    /**
     * 依据 JDBC 产品名映射到方言族（大小写不敏感、关键字包含匹配）
     */
    public static SqlDialect fromProductName(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return DEFAULT;
        }
        String p = productName.toLowerCase(Locale.ROOT);
        if (containsAny(p, "mariadb", "mysql", "tidb", "percona", "oceanbase", "polar-x")) {
            return MYSQL;
        }
        if (containsAny(p, "postgres", "opengauss", "kingbase", "edb", "cockroach", "gauss", "polar")) {
            return POSTGRES;
        }
        if (containsAny(p, "sql server", "azure sql", "sybase")) {
            return SQL_SERVER;
        }
        if (containsAny(p, "dameng", "dm dbms")) {
            return DM;
        }
        if (containsAny(p, "oracle")) {
            return ORACLE;
        }
        if (containsAny(p, "h2")) {
            return H2;
        }
        if (containsAny(p, "sqlite")) {
            return SQLITE;
        }
        if (containsAny(p, "hsql")) {
            return H2;
        }
        return DEFAULT;
    }

    /**
     * 按配置别名（如 "mysql"、"postgresql"、"sqlserver"、"oracle"、"sqlite"、"h2"、"dm"、"default"）
     * 查找方言；未知别名抛异常。
     */
    public static SqlDialect byName(String name) {
        if (name == null) {
            return DEFAULT;
        }
        SqlDialect dialect = BY_ALIAS.get(name.trim().toLowerCase(Locale.ROOT));
        if (dialect == null) {
            throw new FeatherDaoException("未知的 SQL 方言配置: [" + name + "]，可选值: "
                    + String.join(", ", BY_ALIAS.keySet()));
        }
        return dialect;
    }

    private static boolean containsAny(String lower, String... keywords) {
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
