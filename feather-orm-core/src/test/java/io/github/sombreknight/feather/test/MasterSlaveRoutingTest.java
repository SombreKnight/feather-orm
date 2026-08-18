package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.datasource.DataSourceHolder;
import io.github.sombreknight.feather.datasource.DataSourceKey;
import io.github.sombreknight.feather.datasource.RoutingDataSource;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一主多从路由测试：读写分离、强制主库、事务内读写一致、默认主库、多从库
 *
 * <p>通过包装 {@link CountingDataSource} 统计每个数据源被获取连接的次数，验证路由行为。</p>
 *
 * @author sombreknight
 */
public class MasterSlaveRoutingTest {

    private static JdbcDataSource rawMaster;
    private static JdbcDataSource rawSlave1;
    private static JdbcDataSource rawSlave2;
    private static CountingDataSource master;
    private static CountingDataSource slave1;
    private static CountingDataSource slave2;
    private static RoutingDataSource routing;
    private static JdbcDAO jdbcDAO;

    @BeforeAll
    public static void init() throws SQLException {
        rawMaster = createDb("feather-routing-master");
        rawSlave1 = createDb("feather-routing-slave1");
        rawSlave2 = createDb("feather-routing-slave2");

        master = new CountingDataSource(rawMaster);
        slave1 = new CountingDataSource(rawSlave1);
        slave2 = new CountingDataSource(rawSlave2);

        createTable(rawMaster);
        createTable(rawSlave1);
        createTable(rawSlave2);

        Map<Object, DataSource> targets = new HashMap<>();
        targets.put(DataSourceKey.MASTER, master);
        targets.put(DataSourceKey.SLAVE_PREFIX + 1, slave1);
        targets.put(DataSourceKey.SLAVE_PREFIX + 2, slave2);
        routing = new RoutingDataSource(targets, master); // 无 Key 时（事务开始等）默认主库

        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        RowMapperSupport support = new RowMapperSupport(registry, new JavassistRowMapperFactory());
        jdbcDAO = new JdbcDAO(new NamedParameterJdbcTemplate(routing), new FixedIdGenerator(), support, 2);
    }

    private static JdbcDataSource createDb(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static void createTable(JdbcDataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE tb_user (" +
                    "id BIGINT PRIMARY KEY," +
                    "user_name VARCHAR(64)," +
                    "age INT," +
                    "status INT," +
                    "type VARCHAR(16)," +
                    "ext_info VARCHAR(512)," +
                    "tags VARCHAR(512)," +
                    "phone_no VARCHAR(32)" +
                    ")");
        }
    }

    @AfterAll
    public static void destroy() {
        DataSourceHolder.clearDataSource();
    }

    @BeforeEach
    public void clean() {
        master.reset();
        slave1.reset();
        slave2.reset();
        executeOn(rawMaster, "DELETE FROM tb_user");
        executeOn(rawSlave1, "DELETE FROM tb_user");
        executeOn(rawSlave2, "DELETE FROM tb_user");
    }

    // ==================== 路由规则 ====================

    @Test
    public void writeGoesToMaster() {
        save("写主库", 1);

        assertEquals(1, master.count(), "写操作必须走主库");
        assertEquals(0, slave1.count());
        assertEquals(0, slave2.count());
    }

    @Test
    public void findByIdGoesToMaster() {
        long id = save("按主键查询", 2);

        master.reset();
        UserEntity found = jdbcDAO.findById(UserEntity.class, id);
        assertNotNull(found);
        assertEquals(1, master.count(), "按主键查询必须走主库（保证读己之写）");
        assertEquals(0, slave1.count());
        assertEquals(0, slave2.count());
    }

    @Test
    public void readGoesToSlave() {
        long id = save("读从库", 3);
        replicate(rawSlave1, id, "读从库", 3); // 模拟主从复制（两个从库都复制，避免随机路由选到空库）
        replicate(rawSlave2, id, "读从库", 3);

        master.reset();
        slave1.reset();
        slave2.reset();
        List<UserEntity> list = jdbcDAO.findList(UserEntity.class,
                " where user_name = :name ", io.github.sombreknight.feather.core.SqlParam.create("name", "读从库"));

        assertEquals(1, list.size(), "从库能读到数据");
        assertEquals(0, master.count(), "普通查询不应走主库");
        assertTrue(slave1.count() + slave2.count() >= 1, "普通查询必须走从库");
    }

    @Test
    public void forceMasterRedirectsReadToMaster() {
        long id = save("强制主库", 4);

        master.reset();
        slave1.reset();
        slave2.reset();
        List<UserEntity> list = jdbcDAO.forceMaster().findList(UserEntity.class,
                " where id = :id ", io.github.sombreknight.feather.core.SqlParam.create("id", id));

        assertEquals(1, list.size(), "强制主库后能读到主库数据");
        assertEquals(1, master.count());
        assertEquals(0, slave1.count(), "forceMaster 后查询不应走从库");
        assertEquals(0, slave2.count());
        assertNull(DataSourceHolder.getDataSourceKey(), "操作完成后数据源 Key 必须清理");
    }

    @Test
    public void multipleSlavesRandomSelection() {
        long id = save("多从库", 5);
        replicate(rawSlave1, id, "多从库", 5);
        replicate(rawSlave2, id, "多从库", 5);

        master.reset();
        slave1.reset();
        slave2.reset();
        jdbcDAO.findList(UserEntity.class, " where user_name = :name ",
                io.github.sombreknight.feather.core.SqlParam.create("name", "多从库"));

        assertEquals(0, master.count());
        boolean exactlyOneSlaveUsed = (slave1.count() == 1 && slave2.count() == 0)
                || (slave1.count() == 0 && slave2.count() == 1);
        assertTrue(exactlyOneSlaveUsed, "多从库时应随机选一个从库: slave1=" + slave1.count() + ", slave2=" + slave2.count());
    }

    // ==================== 事务 ====================

    @Test
    public void transactionUsesMasterAndReadsOwnWrites() {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(routing));

        master.reset();
        slave1.reset();
        slave2.reset();
        tx.executeWithoutResult(status -> {
            long id = save("事务内读写", 6);
            List<UserEntity> list = jdbcDAO.findList(UserEntity.class, " where id = :id ",
                    io.github.sombreknight.feather.core.SqlParam.create("id", id));
            assertEquals(1, list.size(), "事务内查询必须读到未提交数据（同一主库连接）");
        });

        assertEquals(1, master.count(), "事务开始仅从主库获取一次连接");
        assertEquals(0, slave1.count(), "事务内所有操作必须复用主库连接，禁止路由到从库");
        assertEquals(0, slave2.count());
    }

    @Test
    public void transactionRollback() {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(routing));

        try {
            tx.executeWithoutResult(status -> {
                save("事务回滚", 7);
                throw new RuntimeException("触发回滚");
            });
        } catch (RuntimeException ignore) {
            // expected
        }

        Long count = jdbcDAO.count(UserEntity.class, " where user_name = :name ",
                io.github.sombreknight.feather.core.SqlParam.create("name", "事务回滚"));
        assertEquals(0L, count, "事务回滚后数据不应存在");
    }

    // ==================== 默认主库 ====================

    @Test
    public void defaultTargetIsMasterWhenNoKeySet() throws SQLException {
        master.reset();
        slave1.reset();
        slave2.reset();
        try (Connection conn = routing.getConnection()) {
            assertNotNull(conn);
        }
        assertEquals(1, master.count(), "未设置数据源 Key 时应默认主库");
        assertEquals(0, slave1.count());
        assertEquals(0, slave2.count());
    }

    @Test
    public void singleNodeModeHasNoRoutingOverhead() {
        // slaveCount=0 → 不设置路由 Key，直接走默认（主）库
        JdbcDAO single = new JdbcDAO(new NamedParameterJdbcTemplate(routing), new FixedIdGenerator(),
                new RowMapperSupport(new TypeHandlerRegistry(), new JavassistRowMapperFactory()), 0);

        master.reset();
        single.save(newUser("单节点", 8));
        single.findList(UserEntity.class, " where user_name = :name ",
                io.github.sombreknight.feather.core.SqlParam.create("name", "单节点"));

        assertNull(DataSourceHolder.getDataSourceKey(), "单节点模式不应设置路由 Key");
    }

    // ==================== 工具 ====================

    private static long save(String name, int age) {
        UserEntity user = newUser(name, age);
        jdbcDAO.save(user);
        return user.getId();
    }

    private static UserEntity newUser(String name, int age) {
        UserEntity user = new UserEntity();
        user.setUserName(name);
        user.setAge(age);
        return user;
    }

    private static void executeOn(JdbcDataSource ds, String sql) {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** 模拟主从复制：把主库某行数据写入指定从库 */
    private static void replicate(JdbcDataSource slave, long id, String name, int age) {
        try (Connection conn = slave.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO tb_user (id, user_name, age) VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, name);
            ps.setInt(3, age);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 递增 id（独立于其它测试类）
     */
    static class FixedIdGenerator implements IdGenerator {
        private long next = 1;

        @Override
        public synchronized long nextId() {
            return next++;
        }
    }

    /**
     * 连接计数数据源：包装真实数据源，统计 getConnection 次数
     */
    static class CountingDataSource implements DataSource {

        private final DataSource delegate;
        private final AtomicInteger connections = new AtomicInteger();

        CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        int count() {
            return connections.get();
        }

        void reset() {
            connections.set(0);
        }

        @Override
        public Connection getConnection() throws SQLException {
            connections.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            connections.incrementAndGet();
            return delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
