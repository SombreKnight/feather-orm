package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.PagingResult;
import io.github.sombreknight.feather.dialect.MySqlDialect;
import io.github.sombreknight.feather.dialect.PostgresDialect;
import io.github.sombreknight.feather.support.LogAccountDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 混合引擎集成测试：默认集群（MySQL）+ others.log（PostgreSQL）
 *
 * <p>验收方言去全局化：同一实体类在两个引擎不同的集群中各自按本库方言生成 SQL，
 * 互不污染；分页 / 读写分离在各集群独立工作。</p>
 *
 * <p>本地需 colima 容器：feather-mysql(3306) + feather-pg(5432)，无容器时跳过。</p>
 *
 * @author sombreknight
 */
@EnabledIf(value = "io.github.sombreknight.feather.test.FeatherMixedEngineTest#databasesAvailable",
        disabledReason = "需要 MySQL(3306) 与 PostgreSQL(5432) 服务容器（CI 服务容器 / 本地 colima）")
@SpringBootTest(classes = {StarterTestApplication.class, LogAccountDAO.class}, properties = {
        "feather.datasource.primary.url=${MYSQL_TEST_URL:jdbc:mysql://localhost:3306/feather_test?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true}",
        "feather.datasource.primary.username=${MYSQL_TEST_USERNAME:feather}",
        "feather.datasource.primary.password=${MYSQL_TEST_PASSWORD:feather}",
        "feather.datasource.others.log.url=${PG_TEST_URL:jdbc:postgresql://localhost:5432/feather_test}",
        "feather.datasource.others.log.username=${PG_TEST_USERNAME:feather}",
        "feather.datasource.others.log.password=${PG_TEST_PASSWORD:feather}",
        "feather.orm.row-mapper=javassist"
})
public class FeatherMixedEngineTest {

    /**
     * 条件探测：本地/CI 的 MySQL 与 PostgreSQL 端口可达才运行（JUnit 条件先于 Spring 上下文加载求值）
     */
    public static boolean databasesAvailable() {
        return canConnect("localhost", 3306) && canConnect("localhost", 5432);
    }

    private static boolean canConnect(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    @Qualifier("jdbcDAO")
    private JdbcDAO defaultJdbcDAO;

    @Autowired
    @Qualifier("logJdbcDAO")
    private JdbcDAO logJdbcDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private LogAccountDAO logAccountDAO;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    @Qualifier("logNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate logTemplate;

    @BeforeEach
    public void init() {
        JdbcTemplate mysql = new JdbcTemplate(namedParameterJdbcTemplate.getJdbcTemplate().getDataSource());
        mysql.execute("CREATE TABLE IF NOT EXISTS tb_account (" +
                "id BIGINT PRIMARY KEY, user_name VARCHAR(64), balance DECIMAL(12,2), status INT)");
        mysql.execute("DELETE FROM tb_account");

        JdbcTemplate pg = new JdbcTemplate(logTemplate.getJdbcTemplate().getDataSource());
        pg.execute("CREATE TABLE IF NOT EXISTS tb_account (" +
                "id BIGINT PRIMARY KEY, user_name VARCHAR(64), balance NUMERIC(12,2), status INT)");
        pg.execute("DELETE FROM tb_account");
    }

    @Test
    public void dialectIsolatedPerCluster() {
        // 同一实体类在两个集群按各自方言映射，互不污染（去全局化验收）
        assertTrue(defaultJdbcDAO.getDialect() instanceof MySqlDialect, "默认集群应为 MySQL 方言");
        assertTrue(logJdbcDAO.getDialect() instanceof PostgresDialect, "log 集群应为 PostgreSQL 方言");
    }

    @Test
    public void crudAndPagingOnBothEngines() {
        // MySQL 集群：保存 + 查询 + 分页
        AccountEntity m = new AccountEntity();
        m.setUserName("mysql-user");
        m.setBalance(new BigDecimal("111.11"));
        m.setStatus(AccountStatus.NORMAL);
        assertTrue(accountDAO.saveEntity(m));
        assertEquals("mysql-user", accountDAO.findById(m.getId()).getUserName());

        // PG 集群：保存 + 查询 + 分页
        AccountEntity p = new AccountEntity();
        p.setUserName("pg-user");
        p.setBalance(new BigDecimal("222.22"));
        p.setStatus(AccountStatus.NORMAL);
        assertTrue(logAccountDAO.saveEntity(p));
        assertEquals("pg-user", logAccountDAO.findById(p.getId()).getUserName());

        // 同 id 不可见（不同物理库）
        assertNull(logAccountDAO.findById(m.getId()));
        assertNull(accountDAO.findById(p.getId()));

        // 分页（LIMIT 族方言）各自执行正常
        PagingResult<AccountEntity> mysqlPage = accountDAO.findPageByPageNum(
                accountDAO.getQueryHelper().withPagination());
        assertEquals(1, mysqlPage.getData().size());

        PagingResult<AccountEntity> pgPage = logAccountDAO.findPageByPageNum(
                logAccountDAO.getQueryHelper().withPagination());
        assertEquals(1, pgPage.getData().size());

        assertNotNull(defaultJdbcDAO);
        assertNotNull(logJdbcDAO);
    }
}
