package io.github.sombreknight.feather.test;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.PagingResult;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feather ORM 核心 CRUD 集成测试（H2 MySQL 模式）
 *
 * @author sombreknight
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class FeatherOrmCrudTest {

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;
    private static UserDAO userDAO;

    @BeforeAll
    public static void init() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:feather;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE tb_user (" +
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
        jdbcDAO = new JdbcDAO(namedParameterJdbcTemplate, new FixedIdGenerator(), support, 0);

        userDAO = new UserDAO();
        ReflectionTestUtils.setField(userDAO, "jdbcDAO", jdbcDAO);
    }

    @BeforeEach
    public void cleanTable() {
        // 每个用例前清空表，保证用例间隔离
        new JdbcTemplate(dataSource).execute("DELETE FROM tb_user");
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    public void t01_saveAndFindById() {
        UserEntity user = newUser("张三", 18, OrderStatus.PAID, TypeEnum.TEST1);

        assertTrue(userDAO.saveEntity(user));
        assertNotNull(user.getId());

        UserEntity found = userDAO.findById(user.getId());
        assertNotNull(found);
        assertEquals("张三", found.getUserName());
        assertEquals(18, found.getAge());
        assertEquals(OrderStatus.PAID, found.getStatus());      // 业务码 round-trip
        assertEquals(TypeEnum.TEST1, found.getType());          // name round-trip
        assertNotNull(found.getExtInfo());                      // JSON 对象 round-trip
        assertEquals(1, found.getExtInfo().getId());
        assertEquals("zcx", found.getExtInfo().getName());
        assertEquals(Arrays.asList("a", "b", "c"), found.getTags()); // 泛型集合 round-trip
        assertEquals("13800138000", found.getPhone());          // @Column 覆盖
    }

    @Test
    public void t02_updateOnlyNonNull() {
        UserEntity user = newUser("李四", 25, OrderStatus.CREATED, TypeEnum.TEST2);
        userDAO.saveEntity(user);

        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setUserName("李四改");
        assertTrue(userDAO.updateEntity(update));

        UserEntity found = userDAO.findById(user.getId());
        assertEquals("李四改", found.getUserName());
        // 未更新的字段保持原值
        assertEquals(25, found.getAge());
        assertEquals(OrderStatus.CREATED, found.getStatus());
        assertNotNull(found.getExtInfo());
    }

    @Test
    public void t03_queryHelper() {
        UserEntity u1 = newUser("王五", 30, OrderStatus.PAID, TypeEnum.TEST1);
        UserEntity u2 = newUser("赵六", 20, OrderStatus.CREATED, TypeEnum.TEST2);
        userDAO.saveEntity(u1);
        userDAO.saveEntity(u2);

        // whereEqual
        List<UserEntity> list = userDAO.findList(userDAO.getQueryHelper().whereEqual("userName", "王五"));
        assertEquals(1, list.size());
        assertEquals("王五", list.get(0).getUserName());

        // 条件组合 + 排序
        list = userDAO.findList(userDAO.getQueryHelper()
                .whereGte("age", 18)
                .orderByDesc("age"));
        assertEquals(2, list.size());
        assertEquals("王五", list.get(0).getUserName()); // age 30 排前

        // count
        long count = userDAO.count(userDAO.getQueryHelper().whereEqual("type", TypeEnum.TEST1));
        assertEquals(1, count);

        // findOne
        UserEntity one = userDAO.findOne(userDAO.getQueryHelper().whereEqual("id", u2.getId()));
        assertNotNull(one);
        assertEquals("赵六", one.getUserName());

        // findField（单字段）
        String name = userDAO.findField(String.class,
                userDAO.getQueryHelper().selectFields("userName").whereEqual("id", u1.getId()).limitOne());
        assertEquals("王五", name);

        // findFieldList
        List<String> names = userDAO.findFieldList(String.class,
                userDAO.getQueryHelper().selectFields("userName").orderByAsc("age"));
        assertEquals(2, names.size());
        assertEquals("赵六", names.get(0)); // age 20 排前

        // limit
        list = userDAO.findList(userDAO.getQueryHelper().orderByAsc("age").limit(1));
        assertEquals(1, list.size());
        assertEquals("赵六", list.get(0).getUserName());
    }

    @Test
    public void t04_pagination() {
        for (int i = 0; i < 5; i++) {
            userDAO.saveEntity(newUser("分页用户" + i, 20 + i, OrderStatus.CREATED, TypeEnum.TEST1));
        }
        PagingResult<UserEntity> result = userDAO.findPageByPageNum(
                userDAO.getQueryHelper().withTotal(true).limit(1, 2));
        assertEquals(5, result.getPageInfo().getTotal());
        assertEquals(2, result.getData().size());
        assertEquals(3, result.getPageInfo().getTotalPage());
    }

    @Test
    public void t05_batch() {
        List<UserEntity> users = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            users.add(newUser("批量" + i, 10 + i, OrderStatus.PAID, TypeEnum.TEST2));
        }
        assertTrue(userDAO.saveEntityList(users));
        for (UserEntity user : users) {
            assertNotNull(user.getId());
        }

        List<Long> ids = new ArrayList<>();
        for (UserEntity user : users) {
            ids.add(user.getId());
        }
        List<UserEntity> found = userDAO.findByIds(ids);
        assertEquals(3, found.size());
        assertNotNull(userDAO.findMapByIds(ids).get(ids.get(0)));
    }

    @Test
    public void t06_nullJsonField() {
        UserEntity user = newUser("空JSON", 40, OrderStatus.CANCELLED, TypeEnum.TEST1);
        user.setExtInfo(null);  // JSON 字段为 null：insert 跳过该列
        user.setTags(null);
        userDAO.saveEntity(user);

        UserEntity found = userDAO.findById(user.getId());
        assertNull(found.getExtInfo());
        assertNull(found.getTags());
    }

    @Test
    public void t07_delete() {
        UserEntity user = newUser("待删除", 50, OrderStatus.CREATED, TypeEnum.TEST1);
        userDAO.saveEntity(user);
        Long id = user.getId();

        assertTrue(userDAO.deleteEntity(user));
        assertNull(userDAO.findById(id));
    }

    @Test
    public void t08_forceMaster() {
        UserEntity user = newUser("主库用户", 60, OrderStatus.PAID, TypeEnum.TEST2);
        userDAO.saveEntity(user);
        userDAO.forceMaster(); // 单节点下为 no-op，不应抛异常
        assertNotNull(userDAO.findById(user.getId()));
    }

    @Test
    public void t09_strictFieldCheck() {
        // 不存在的字段名应 fail-fast 抛异常，而不是拼进 SQL
        boolean thrown = false;
        try {
            userDAO.findList(userDAO.getQueryHelper().whereEqual("notExistField", "x"));
        } catch (Exception e) {
            thrown = true;
        }
        assertTrue(thrown, "未知字段应抛异常");
    }

    @Test
    public void t10_updateBatch() {
        UserEntity u1 = newUser("批量改1", 30, OrderStatus.PAID, TypeEnum.TEST1);
        UserEntity u2 = newUser("批量改2", 40, OrderStatus.CREATED, TypeEnum.TEST2);
        userDAO.saveEntity(u1);
        userDAO.saveEntity(u2);

        // 仅设置部分字段：u1 改名字，u2 改 age；其余字段为 null（不得触碰原值）
        UserEntity up1 = new UserEntity();
        up1.setId(u1.getId());
        up1.setUserName("批量改1-新");
        UserEntity up2 = new UserEntity();
        up2.setId(u2.getId());
        up2.setAge(50);

        assertTrue(userDAO.updateEntityList(Arrays.asList(up1, up2)));

        UserEntity f1 = userDAO.findById(u1.getId());
        assertEquals("批量改1-新", f1.getUserName());
        assertEquals(30, f1.getAge());
        assertEquals(OrderStatus.PAID, f1.getStatus());
        assertEquals(TypeEnum.TEST1, f1.getType());

        UserEntity f2 = userDAO.findById(u2.getId());
        assertEquals("批量改2", f2.getUserName());
        assertEquals(50, f2.getAge());
        assertEquals(OrderStatus.CREATED, f2.getStatus());
        assertNotNull(f2.getExtInfo());
    }

    // ==================== 工具 ====================

    private static UserEntity newUser(String name, int age, OrderStatus status, TypeEnum type) {
        UserEntity user = new UserEntity();
        user.setUserName(name);
        user.setAge(age);
        user.setStatus(status);
        user.setType(type);
        ExtInfo extInfo = new ExtInfo();
        extInfo.setId(1);
        extInfo.setName("zcx");
        user.setExtInfo(extInfo);
        user.setTags(Arrays.asList("a", "b", "c"));
        user.setPhone("13800138000");
        return user;
    }

    /**
     * 固定递增 ID 生成器（测试用）
     */
    static class FixedIdGenerator implements IdGenerator {
        private long next = 1;

        @Override
        public synchronized long nextId() {
            return next++;
        }
    }
}
