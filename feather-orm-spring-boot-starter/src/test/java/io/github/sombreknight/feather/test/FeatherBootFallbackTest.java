package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.JdbcDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回退场景测试：未配置 feather.orm.datasource 时，框架回退 Spring Boot 默认数据源，
 * 默认 JdbcDAO / TransactionTemplate / DAO 仍正常工作（旧版行为兼容）
 *
 * @author sombreknight
 */
@SpringBootTest(classes = StarterTestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:feather-boot;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
public class FeatherBootFallbackTest {

    @Autowired
    private JdbcDAO jdbcDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private AccountDAO accountDAO;

    @BeforeEach
    public void init() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(namedParameterJdbcTemplate.getJdbcTemplate().getDataSource());
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS tb_account (" +
                "id BIGINT PRIMARY KEY," +
                "user_name VARCHAR(64)," +
                "balance DECIMAL(12,2)," +
                "status INT" +
                ")");
        jdbcTemplate.execute("DELETE FROM tb_account");
    }

    @Test
    public void fallbackWiring() {
        assertNotNull(jdbcDAO);
        assertNotNull(transactionTemplate);
        assertNotNull(accountDAO);
    }

    @Test
    public void crudOnBootDataSource() {
        AccountEntity e = new AccountEntity();
        e.setUserName("boot-user");
        e.setBalance(new BigDecimal("5.00"));
        e.setStatus(AccountStatus.NORMAL);
        assertTrue(accountDAO.saveEntity(e));
        assertEquals("boot-user", accountDAO.findById(e.getId()).getUserName());
        assertEquals(1, accountDAO.count(accountDAO.getQueryHelper()));
    }

    @Test
    public void transactionRollbackOnBootDataSource() {
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            AccountEntity e = new AccountEntity();
            e.setUserName("tx-user");
            e.setBalance(new BigDecimal("5.00"));
            e.setStatus(AccountStatus.NORMAL);
            accountDAO.saveEntity(e);
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, accountDAO.count(accountDAO.getQueryHelper()));
    }
}
