package io.github.sombreknight.feather.dialect;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 方言注册表：产品名映射、配置别名、DataSource 自动探测
 *
 * @author sombreknight
 */
public class DialectRegistryTest {

    @Test
    public void productNameMapping() {
        assertEquals("MySQL", DialectRegistry.fromProductName("MySQL").getName());
        assertEquals("MySQL", DialectRegistry.fromProductName("MariaDB").getName());
        assertEquals("MySQL", DialectRegistry.fromProductName("TiDB").getName());
        assertEquals("MySQL", DialectRegistry.fromProductName("OceanBase").getName());
        assertEquals("PostgreSQL", DialectRegistry.fromProductName("PostgreSQL").getName());
        assertEquals("PostgreSQL", DialectRegistry.fromProductName("openGauss").getName());
        assertEquals("PostgreSQL", DialectRegistry.fromProductName("KingbaseES").getName());
        assertEquals("SQL Server", DialectRegistry.fromProductName("Microsoft SQL Server").getName());
        assertEquals("SQL Server", DialectRegistry.fromProductName("Azure SQL Database").getName());
        assertEquals("Oracle", DialectRegistry.fromProductName("Oracle").getName());
        assertEquals("H2", DialectRegistry.fromProductName("H2").getName());
        assertEquals("SQLite", DialectRegistry.fromProductName("SQLite").getName());
        assertEquals("DM", DialectRegistry.fromProductName("DM DBMS").getName());
        // 未知产品回退兜底方言
        assertEquals("Default", DialectRegistry.fromProductName("Some Weird DB").getName());
        assertEquals("Default", DialectRegistry.fromProductName(null).getName());
    }

    @Test
    public void configAlias() {
        assertInstanceOf(MySqlDialect.class, DialectRegistry.byName("mysql"));
        assertInstanceOf(MySqlDialect.class, DialectRegistry.byName("MySQL"));
        assertInstanceOf(PostgresDialect.class, DialectRegistry.byName("postgresql"));
        assertInstanceOf(SqlServerDialect.class, DialectRegistry.byName("sqlserver"));
        assertInstanceOf(OracleDialect.class, DialectRegistry.byName("oracle"));
        assertInstanceOf(SqliteDialect.class, DialectRegistry.byName("sqlite"));
        assertInstanceOf(H2Dialect.class, DialectRegistry.byName("h2"));
        assertInstanceOf(DmDialect.class, DialectRegistry.byName("dm"));
        assertInstanceOf(DefaultDialect.class, DialectRegistry.byName("default"));
        assertThrows(io.github.sombreknight.feather.exception.FeatherDaoException.class,
                () -> DialectRegistry.byName("not-a-db"));
    }

    @Test
    public void detectFromDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:detect;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        assertEquals("H2", DialectRegistry.detect(ds).getName());
        ds.close();
    }
}
