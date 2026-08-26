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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * starter 集成测试：验证自动配置接管数据源、Bean 装配、CRUD、事务
 *
 * @author sombreknight
 */
@SpringBootTest(classes = StarterTestApplication.class, properties = {
        "feather.orm.datasource.primary.url=jdbc:h2:mem:feather-starter;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.orm.datasource.primary.username=sa",
        "feather.orm.datasource.primary.password="

})
public class FeatherStarterTest {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcDAO jdbcDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    public void contextWiring() {
        assertNotNull(namedParameterJdbcTemplate);
        assertNotNull(jdbcDAO);
        assertNotNull(transactionTemplate);
        assertNotNull(accountDAO);
    }

    @Test
    public void crud() {
        AccountEntity account = new AccountEntity();
        account.setUserName("张三");
        account.setBalance(new BigDecimal("100.50"));
        account.setStatus(AccountStatus.NORMAL);

        assertNotNull(jdbcDAO.save(account));
        assertNotNull(account.getId());

        AccountEntity found = accountDAO.findById(account.getId());
        assertNotNull(found);
        assertEquals("张三", found.getUserName());
        assertEquals(0, new BigDecimal("100.50").compareTo(found.getBalance()));
        assertEquals(AccountStatus.NORMAL, found.getStatus());

        // 查询条件（枚举参数自动转换）
        AccountEntity byName = accountDAO.findOne(accountDAO.getQueryHelper().whereEqual(AccountEntity::getUserName, "张三"));
        assertNotNull(byName);
        assertEquals(account.getId(), byName.getId());

        // 更新
        AccountEntity update = new AccountEntity();
        update.setId(account.getId());
        update.setBalance(new BigDecimal("200.00"));
        assertNotNull(accountDAO.updateEntity(update));

        AccountEntity afterUpdate = accountDAO.findById(account.getId());
        assertEquals(0, new BigDecimal("200.00").compareTo(afterUpdate.getBalance()));
        assertEquals("张三", afterUpdate.getUserName()); // 未更新字段保持

        // 删除
        accountDAO.deleteEntity(afterUpdate);
        assertNull(accountDAO.findById(account.getId()));
    }

    @Test
    public void transactionRollback() {
        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            AccountEntity account = new AccountEntity();
            account.setUserName("回滚用户");
            account.setStatus(AccountStatus.FROZEN);
            accountDAO.saveEntity(account);
            throw new RuntimeException("触发回滚");
        }));

        long count = accountDAO.count(accountDAO.getQueryHelper().whereEqual(AccountEntity::getUserName, "回滚用户"));
        assertEquals(0, count, "事务回滚后数据不应存在");
    }
}
