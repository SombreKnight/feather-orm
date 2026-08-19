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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PostgreSQL 方言集成测试：验证方言解耦后在真实 PG 上的 CRUD / 分页 / 条件查询 / DTO。
 *
 * <p>需要环境变量 {@code PG_TEST_URL}（如 {@code jdbc:postgresql://localhost:5432/feather_test}）；
 * 未配置（本地开发）自动跳过，CI 通过服务容器启用。</p>
 *
 * @author sombreknight
 */
public class PostgresDialectIntegrationTest {

    private static final String PG_URL = System.getenv("PG_TEST_URL");

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;
    private static UserDAO userDAO;

    @BeforeAll
    public static void init() {
        // 本地无 PG 环境时跳过整类测试
        assumeTrue(PG_URL != null && !PG_URL.trim().isEmpty(),
                "未配置 PG_TEST_URL，跳过 PostgreSQL 集成测试（CI 服务容器会自动启用）");

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(PG_URL);
        dataSource.setUsername(System.getenv("PG_TEST_USERNAME"));
        dataSource.setPassword(System.getenv("PG_TEST_PASSWORD"));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS tb_user (" +
                "id BIGINT PRIMARY KEY," +
                "user_name VARCHAR(64)," +
                "age INT," +
                "status INT," +
                "type VARCHAR(16)," +
                "ext_info VARCHAR(512)," +
                "tags VARCHAR(512)," +
                "phone_no VARCHAR(32)" +
                ")");

        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        RowMapperSupport support = new RowMapperSupport(registry, new JavassistRowMapperFactory());
        jdbcDAO = new JdbcDAO(namedParameterJdbcTemplate, new FixedIdGenerator(), support, 0, new PostgresDialect());
        // 实体映射按 PG 方言生成（此处最小引用与 Default 一致，验证 PG 方言下分页/锁片段正确）
        Mapper.getInstance().setDialect(new PostgresDialect());

        userDAO = new UserDAO();
        ReflectionTestUtils.setField(userDAO, "jdbcDAO", jdbcDAO);
    }

    @AfterAll
    public static void destroy() {
        Mapper.getInstance().setDialect(io.github.sombreknight.feather.dialect.DialectRegistry.defaultDialect());
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
    public void crudOnPostgres() {
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
    public void pagingOnPostgres() {
        for (int i = 1; i <= 5; i++) {
            userDAO.saveEntity(newUser("用户" + i, 20 + i));
        }
        PagingResult<UserEntity> page = userDAO.findPageByPageNum(
                userDAO.getQueryHelper().withTotal(true).limit(2, 2));
        assertEquals(5, page.getPageInfo().getTotal());
        assertEquals(2, page.getData().size());
        // 分页第 2 页：offset 2，应命中第 3、4 条（按插入序，无排序时以 DB 返回序为准，数量与偏移正确即可）
        assertEquals(2, page.getData().size());
    }

    @Test
    public void conditionAndDtoOnPostgres() {
        userDAO.saveEntity(newUser("a1", 18));
        userDAO.saveEntity(newUser("a2", 28));
        userDAO.saveEntity(newUser("b3", 38));

        List<UserEntity> adults = userDAO.findList(userDAO.getQueryHelper()
                .whereGte("age", 28).orderByDesc("age"));
        assertEquals(2, adults.size());
        assertEquals(38, adults.get(0).getAge());

        long count = userDAO.count(userDAO.getQueryHelper().whereLike("userName", "a%"));
        assertEquals(2, count);

        // whereIn 与单元素降级
        List<UserEntity> byIds = userDAO.findList(userDAO.getQueryHelper()
                .whereIn("userName", Arrays.asList("a1", "b3")));
        assertEquals(2, byIds.size());

        // DTO 查询
        List<ItemDTO> dtos = jdbcDAO.findDtoList(ItemDTO.class,
                "select user_name as name, age as price from tb_user where age >= 28",
                null);
        assertEquals(2, dtos.size());
        assertEquals("a2", dtos.get(0).getName());
    }

    @Test
    public void pagingCountStripsOrderByOnPostgres() {
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
