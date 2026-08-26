package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.support.MultiTxService;
import io.github.sombreknight.feather.support.OrderAccountDAO;
import io.github.sombreknight.feather.support.PrimaryAliasAccountDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多数据源集成测试：默认集群 + others.order 集群，验证数据隔离、DAO 归属解析、Map 注入
 *
 * @author sombreknight
 */
@SpringBootTest(classes = {StarterTestApplication.class, OrderAccountDAO.class,
        PrimaryAliasAccountDAO.class, MultiTxService.class}, properties = {
        "feather.orm.datasource.primary.url=jdbc:h2:mem:feather-multi-main;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.orm.datasource.primary.username=sa",
        "feather.orm.datasource.primary.password=",
        "feather.orm.datasource.others.order.url=jdbc:h2:mem:feather-multi-order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.orm.datasource.others.order.username=sa",
        "feather.orm.datasource.others.order.password=",
        "feather.orm.datasource.others.log.url=jdbc:h2:mem:feather-multi-log;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.orm.datasource.others.log.username=sa",
        "feather.orm.datasource.others.log.password="

})
public class FeatherMultiDataSourceTest {

    @Autowired
    private JdbcDAO jdbcDAO;

    @Autowired
    private Map<String, JdbcDAO> jdbcDAOMap;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private OrderAccountDAO orderAccountDAO;

    @Autowired
    private PrimaryAliasAccountDAO primaryAliasAccountDAO;

    @Autowired
    private MultiTxService multiTxService;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    @Qualifier("orderNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate orderTemplate;

    @BeforeEach
    public void init() {
        JdbcTemplate main = new JdbcTemplate(namedParameterJdbcTemplate.getJdbcTemplate().getDataSource());
        main.execute("CREATE TABLE IF NOT EXISTS tb_account (" +
                "id BIGINT PRIMARY KEY," +
                "user_name VARCHAR(64)," +
                "balance DECIMAL(12,2)," +
                "status INT" +
                ")");
        main.execute("DELETE FROM tb_account");

        JdbcTemplate order = new JdbcTemplate(orderTemplate.getJdbcTemplate().getDataSource());
        order.execute("CREATE TABLE IF NOT EXISTS tb_account (" +
                "id BIGINT PRIMARY KEY," +
                "user_name VARCHAR(64)," +
                "balance DECIMAL(12,2)," +
                "status INT" +
                ")");
        order.execute("DELETE FROM tb_account");
    }

    @Test
    public void allClustersRegisteredInMap() {
        // 默认集群主 bean + 命名集群（Map 注入 key 为 bean 名，不含别名）
        assertNotNull(jdbcDAOMap.get("jdbcDAO"));
        assertNotNull(jdbcDAOMap.get("orderJdbcDAO"));
        assertNotNull(jdbcDAOMap.get("logJdbcDAO"));
        assertTrue(jdbcDAOMap.size() >= 3);
    }

    @Test
    public void defaultAliasAnnotationFallsBackToDefaultCluster() {
        // @FeatherDataSource("primary") 未显式注册 primaryJdbcDAO，应回退默认集群主 bean
        AccountEntity e = new AccountEntity();
        e.setUserName("alias-user");
        e.setBalance(new BigDecimal("9.00"));
        e.setStatus(AccountStatus.NORMAL);
        assertTrue(primaryAliasAccountDAO.saveEntity(e));
        assertEquals("alias-user", primaryAliasAccountDAO.findById(e.getId()).getUserName());
    }

    @Test
    public void daoBindsToOwnCluster() {
        assertNotNull(accountDAO);
        assertNotNull(orderAccountDAO);
    }

    @Test
    public void dataIsolatedBetweenClusters() {
        // 同一主键 id=100 分别写入默认集群与 order 集群，互不可见
        AccountEntity mainEntity = new AccountEntity();
        mainEntity.setId(100L);
        mainEntity.setUserName("main-user");
        mainEntity.setBalance(new BigDecimal("100.00"));
        mainEntity.setStatus(AccountStatus.NORMAL);
        assertTrue(accountDAO.saveEntity(mainEntity));

        AccountEntity orderEntity = new AccountEntity();
        orderEntity.setId(100L);
        orderEntity.setUserName("order-user");
        orderEntity.setBalance(new BigDecimal("200.00"));
        orderEntity.setStatus(AccountStatus.NORMAL);
        assertTrue(orderAccountDAO.saveEntity(orderEntity));

        // 各自集群读到自己的数据
        assertEquals("main-user", accountDAO.findById(100L).getUserName());
        assertEquals("order-user", orderAccountDAO.findById(100L).getUserName());

        // 跨集群不可见（不同物理库）
        assertNull(orderAccountDAO.findById(999L));
    }

    @Test
    public void countAndListOnOwnCluster() {
        AccountEntity e1 = new AccountEntity();
        e1.setUserName("m1");
        e1.setBalance(new BigDecimal("1.00"));
        e1.setStatus(AccountStatus.NORMAL);
        accountDAO.saveEntity(e1);

        AccountEntity e2 = new AccountEntity();
        e2.setUserName("o1");
        e2.setBalance(new BigDecimal("2.00"));
        e2.setStatus(AccountStatus.NORMAL);
        orderAccountDAO.saveEntity(e2);

        assertEquals(1, accountDAO.count(accountDAO.getQueryHelper()));
        assertEquals(1, orderAccountDAO.count(orderAccountDAO.getQueryHelper()));

        // 各集群方言一致（H2 MODE=MySQL 探测为 MySQL 方言），分页可正常执行
        assertEquals(1, accountDAO.findList(accountDAO.getQueryHelper()).size());
        assertEquals(1, orderAccountDAO.findList(orderAccountDAO.getQueryHelper()).size());
    }

    @Test
    public void transactionOnOrderClusterRollsBack() {
        assertThrows(IllegalStateException.class, () -> multiTxService.saveAndRollback());
        assertEquals(0, orderAccountDAO.count(orderAccountDAO.getQueryHelper()));
    }

    @Test
    public void transactionOnOrderClusterReadsOwnWrites() {
        AccountEntity saved = multiTxService.saveAndReadOwnWrite();
        assertNotNull(saved);
        assertEquals("tx-own-write", saved.getUserName());
    }
}
