package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.datasource.RoutingDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * starter 主从集成测试：自动配置构建路由数据源、读写分离、事务
 *
 * @author sombreknight
 */
@SpringBootTest(classes = StarterTestApplication.class, properties = {
        "feather.datasource.primary.url=jdbc:h2:mem:feather-starter-master;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.datasource.primary.username=sa",
        "feather.datasource.primary.password=",
        "feather.datasource.replicas[0].url=jdbc:h2:mem:feather-starter-slave;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.datasource.replicas[0].username=sa",
        "feather.datasource.replicas[0].password="
})
public class FeatherStarterReplicaTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcDAO jdbcDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AccountDAO accountDAO;

    @BeforeEach
    public void init() throws Exception {
        // 主从库各自建表（模拟复制前 schema 一致）
        String masterUrl = "jdbc:h2:mem:feather-starter-master;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        String slaveUrl = "jdbc:h2:mem:feather-starter-slave;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        for (String url : new String[]{masterUrl, slaveUrl}) {
            try (Connection conn = DriverManager.getConnection(url, "sa", "");
                 Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS tb_account (" +
                        "id BIGINT PRIMARY KEY," +
                        "user_name VARCHAR(64)," +
                        "balance DECIMAL(12,2)," +
                        "status INT" +
                        ")");
                st.execute("DELETE FROM tb_account");
            }
        }
    }

    @Test
    public void routingDataSourceConfigured() {
        assertTrue(dataSource instanceof RoutingDataSource, "配置了 replicas 后应使用路由数据源");
    }

    @Test
    public void crudWithReplicas() {
        AccountEntity account = new AccountEntity();
        account.setUserName("主从测试");
        account.setBalance(new BigDecimal("66.60"));
        account.setStatus(AccountStatus.NORMAL);

        jdbcDAO.save(account);
        assertNotNull(account.getId());

        // 按主键查询走主库 → 能读到
        AccountEntity found = accountDAO.findById(account.getId());
        assertNotNull(found);
        assertEquals("主从测试", found.getUserName());

        // 普通查询走从库（从库无数据，模拟复制延迟）→ 空；强制主库 → 能读到
        List<AccountEntity> fromSlave = accountDAO.findList(accountDAO.getQueryHelper()
                .whereEqual("userName", "主从测试"));
        assertTrue(fromSlave.isEmpty(), "从库未复制数据时普通查询应为空");

        accountDAO.forceMaster();
        List<AccountEntity> fromMaster = accountDAO.findList(accountDAO.getQueryHelper()
                .whereEqual("userName", "主从测试"));
        assertEquals(1, fromMaster.size());
    }

    @Test
    public void transactionWithReplicasReadsOwnWrites() {
        transactionTemplate.executeWithoutResult(status -> {
            AccountEntity account = new AccountEntity();
            account.setUserName("事务主从");
            account.setStatus(AccountStatus.FROZEN);
            jdbcDAO.save(account);

            // 事务内查询必须复用主库连接（读己之写）
            List<AccountEntity> list = accountDAO.findList(accountDAO.getQueryHelper()
                    .whereEqual("userName", "事务主从"));
            assertEquals(1, list.size());
        });
    }

    @Test
    public void transactionRollbackWithReplicas() {
        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            AccountEntity account = new AccountEntity();
            account.setUserName("回滚主从");
            jdbcDAO.save(account);
            throw new RuntimeException("回滚");
        }));
        assertEquals(0, accountDAO.count(accountDAO.getQueryHelper().whereEqual("userName", "回滚主从")));
    }
}
