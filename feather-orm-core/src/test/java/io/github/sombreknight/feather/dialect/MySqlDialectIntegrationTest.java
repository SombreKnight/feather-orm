package io.github.sombreknight.feather.dialect;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.PagingResult;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.Mapper;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.test.ItemDTO;
import io.github.sombreknight.feather.test.UserDAO;
import io.github.sombreknight.feather.test.UserEntity;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySQL 方言集成测试：真实 MySQL 上验证 CRUD / 分页 / 条件查询 / DTO / FORCE INDEX。
 *
 * <p>需要环境变量 {@code MYSQL_TEST_URL}（如 {@code jdbc:mysql://localhost:3306/feather_test?...}）；
 * 未配置（本地开发）自动跳过，CI 通过服务容器启用。</p>
 *
 * @author sombreknight
 */
public class MySqlDialectIntegrationTest {

    private static final String MYSQL_URL = System.getenv("MYSQL_TEST_URL");

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;
    private static UserDAO userDAO;

    @BeforeAll
    public static void init() {
        assumeTrue(MYSQL_URL != null && !MYSQL_URL.trim().isEmpty(),
                "未配置 MYSQL_TEST_URL，跳过 MySQL 集成测试（CI 服务容器会自动启用）");

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(MYSQL_URL);
        dataSource.setUsername(System.getenv("MYSQL_TEST_USERNAME"));
        dataSource.setPassword(System.getenv("MYSQL_TEST_PASSWORD"));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS tb_user (" +
                "id BIGINT PRIMARY KEY," +
                "user_name VARCHAR(64)," +
                "age INT," +
                "status INT," +
                "type VARCHAR(16)," +
                "ext_info VARCHAR(512)," +
                "tags VARCHAR(512)," +
                "phone_no VARCHAR(32)," +
                "INDEX idx_user_name (user_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        RowMapperSupport support = new RowMapperSupport(registry, new JavassistRowMapperFactory());
        jdbcDAO = new JdbcDAO(namedParameterJdbcTemplate, new FixedIdGenerator(), support, 0, new MySqlDialect());
        Mapper.getInstance().setDialect(new MySqlDialect());

        userDAO = new UserDAO();
        ReflectionTestUtils.setField(userDAO, "jdbcDAO", jdbcDAO);
    }

    @AfterAll
    public static void destroy() {
        Mapper.getInstance().setDialect(DialectRegistry.defaultDialect());
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    public void cleanTable() {
        new JdbcTemplate(dataSource).execute("DELETE FROM tb_user");
    }

    private UserEntity newUser(String name, int age) {
        UserEntity user = new UserEntity();
        user.setUserName(name);
        user.setAge(age);
        user.setStatus(io.github.sombreknight.feather.test.OrderStatus.PAID);
        return user;
    }

    @Test
    public void crudOnMySql() {
        UserEntity user = newUser("张三", 30);
        assertTrue(userDAO.saveEntity(user));
        assertNotNull(user.getId());

        UserEntity found = userDAO.findById(user.getId());
        assertEquals("张三", found.getUserName());
        assertEquals(30, found.getAge());

        found.setUserName("李四改");
        assertTrue(userDAO.updateEntity(found));
        assertEquals("李四改", userDAO.findById(user.getId()).getUserName());

        assertTrue(userDAO.deleteEntity(found));
        assertNull(userDAO.findById(user.getId()));
    }

    @Test
    public void pagingOnMySql() {
        for (int i = 1; i <= 5; i++) {
            userDAO.saveEntity(newUser("用户" + i, 20 + i));
        }
        PagingResult<UserEntity> page = userDAO.findPageByPageNum(
                userDAO.getQueryHelper().withTotal(true).limit(2, 2));
        assertEquals(5, page.getPageInfo().getTotal());
        assertEquals(2, page.getData().size());
    }

    @Test
    public void conditionDtoAndForceIndexOnMySql() {
        userDAO.saveEntity(newUser("a1", 18));
        userDAO.saveEntity(newUser("a2", 28));
        userDAO.saveEntity(newUser("b3", 38));

        List<UserEntity> adults = userDAO.findList(userDAO.getQueryHelper()
                .whereGte("age", 28).orderByDesc("age"));
        assertEquals(2, adults.size());
        assertEquals(38, adults.get(0).getAge());

        // FORCE INDEX（MySQL 专有能力）
        List<UserEntity> indexed = userDAO.findList(userDAO.getQueryHelper()
                .forceIndex("idx_user_name").whereEqual("userName", "a1"));
        assertEquals(1, indexed.size());

        // whereIn
        List<UserEntity> byIds = userDAO.findList(userDAO.getQueryHelper()
                .whereIn("userName", Arrays.asList("a1", "b3")));
        assertEquals(2, byIds.size());

        // DTO 查询
        List<ItemDTO> dtos = jdbcDAO.findDtoList(ItemDTO.class,
                "select user_name as name, age as price from tb_user where age >= 28",
                null);
        assertEquals(2, dtos.size());
    }

    @Test
    public void pagingCountStripsOrderByOnMySql() {
        for (int i = 1; i <= 3; i++) {
            userDAO.saveEntity(newUser("u" + i, i));
        }
        PagingResult<UserEntity> page = userDAO.findPageByPageNum(
                userDAO.getQueryHelper().orderByAsc("age").withTotal(true).limit(1, 2));
        assertEquals(3, page.getPageInfo().getTotal());
        assertEquals(2, page.getData().size());
    }

    /** 固定 id 生成器，保证测试可预期 */
    public static class FixedIdGenerator implements IdGenerator {
        private long seq = 1000;

        @Override
        public long nextId() {
            return ++seq;
        }
    }
}
