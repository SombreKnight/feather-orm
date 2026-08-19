package io.github.sombreknight.feather.test;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.PagingResult;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.ReflectionRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯反射 RowMapper（ReflectionRowMapperFactory）集成测试
 *
 * <p>验收 issue #3「反射模式功能行为完全不变」：优化（构造器缓存 + MethodHandle 字段访问）后，
 * CRUD / 分页 / DTO 缺列跳过 / 原始类型 null / 无 setter 字段映射均与 Javassist 模式语义一致。</p>
 *
 * @author sombreknight
 */
public class ReflectionRowMapperCrudTest {

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;
    private static UserDAO userDAO;

    @BeforeAll
    public static void init() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:feather-reflect;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
        jdbcTemplate.execute("CREATE TABLE tb_no_setter (" +
                "id BIGINT PRIMARY KEY," +
                "name VARCHAR(64)," +
                "count INT," +
                "enabled BOOLEAN" +
                ")");

        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        // 关键差异：使用纯反射 RowMapper 工厂
        RowMapperSupport support = new RowMapperSupport(registry, new ReflectionRowMapperFactory());
        jdbcDAO = new JdbcDAO(namedParameterJdbcTemplate, new FixedIdGenerator(), support, 0);

        userDAO = new UserDAO();
        ReflectionTestUtils.setField(userDAO, "jdbcDAO", jdbcDAO);
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    public void cleanTable() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DELETE FROM tb_user");
        jdbcTemplate.execute("DELETE FROM tb_no_setter");
    }

    @Test
    public void crudWithSetterEntity() {
        UserEntity user = new UserEntity();
        user.setUserName("反射模式");
        user.setAge(30);
        user.setStatus(OrderStatus.PAID);
        assertTrue(userDAO.saveEntity(user));
        assertNotNull(user.getId());

        UserEntity found = userDAO.findById(user.getId());
        assertEquals("反射模式", found.getUserName());
        assertEquals(30, found.getAge());
        assertEquals(OrderStatus.PAID, found.getStatus());

        found.setAge(31);
        assertTrue(userDAO.updateEntity(found));
        assertEquals(31, userDAO.findById(user.getId()).getAge());

        assertTrue(userDAO.deleteEntity(found));
        assertEquals(0, userDAO.count(userDAO.getQueryHelper()));
    }

    @Test
    public void pagingAndConditionOnReflection() {
        for (int i = 1; i <= 5; i++) {
            UserEntity user = new UserEntity();
            user.setUserName("u" + i);
            user.setAge(20 + i);
            userDAO.saveEntity(user);
        }
        PagingResult<UserEntity> page = userDAO.findPageByPageNum(
                userDAO.getQueryHelper().withTotal(true).limit(2, 2));
        assertEquals(5, page.getPageInfo().getTotal());
        assertEquals(2, page.getData().size());

        List<UserEntity> adults = userDAO.findList(userDAO.getQueryHelper()
                .whereGte("age", 23).orderByDesc("age"));
        assertEquals(3, adults.size());
    }

    /**
     * 无 setter 的私有字段（走字段 MethodHandle 路径）+ 原始类型 + boolean（isX 路径）映射
     */
    @Test
    public void noSetterFieldsMappedViaMethodHandle() {
        NoSetterEntity entity = new NoSetterEntity();
        entity.setId(1001L);
        entity.name = "无setter字段";
        entity.count = 7;
        entity.enabled = true;
        assertEquals(1, jdbcDAO.save(entity));

        NoSetterEntity found = jdbcDAO.findById(NoSetterEntity.class, 1001L);
        assertEquals("无setter字段", found.name);
        assertEquals(7, found.count);
        assertEquals(true, found.enabled);

        // 更新（写库路径也走 MethodHandle 读取）
        found.name = "改名";
        assertEquals(1, jdbcDAO.update(found));
        assertEquals("改名", jdbcDAO.findById(NoSetterEntity.class, 1001L).name);
    }

    /**
     * 原始类型字段写入 null 应失败（与原 Field.set 行为一致，DTO 模式除外）
     */
    @Test
    public void primitiveNullRejectedOnEntityMapping() {
        NoSetterEntity entity = new NoSetterEntity();
        entity.setId(2001L);
        entity.name = "x";
        entity.count = 1;
        entity.enabled = false;
        jdbcDAO.save(entity);

        // 直接把 count 置 NULL，回读时实体映射应抛异常
        new JdbcTemplate(dataSource).execute("UPDATE tb_no_setter SET count = NULL WHERE id = 2001");
        assertThrows(Exception.class, () -> jdbcDAO.findById(NoSetterEntity.class, 2001L));
    }

    /**
     * DTO 模式：查询列缺失自动跳过（不回写、不抛异常）；原始类型列 NULL 也跳过
     */
    @Test
    public void dtoMissingColumnSkipped() {
        NoSetterEntity entity = new NoSetterEntity();
        entity.setId(3001L);
        entity.name = "dto";
        entity.count = 3;
        entity.enabled = true;
        jdbcDAO.save(entity);

        // DTO 有 name/enabled/count 字段，但查询只返回 id、name（缺 count/enabled 列 → 跳过）
        PartialDto dto = jdbcDAO.findDto(PartialDto.class,
                "select id, name from tb_no_setter where id = 3001", null);
        assertNotNull(dto);
        assertEquals(3001L, dto.getId());
        assertEquals("dto", dto.getName());
    }

    /**
     * 无 setter 实体：字段均私有且无 getter/setter，验证走字段 MethodHandle 路径。
     * （嵌套类可直接访问私有字段赋值）
     */
    @Table("tb_no_setter")
    public static class NoSetterEntity extends BaseEntity {
        private String name;
        private int count;
        private boolean enabled;
    }

    /** 只读 DTO：比查询列多的字段在缺列时跳过 */
    public static class PartialDto {
        private Long id;
        private String name;
        private int count;    // 查询无此列 → 跳过
        private boolean enabled; // 查询无此列 → 跳过

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class FixedIdGenerator implements IdGenerator {
        private long seq = 0;

        @Override
        public long nextId() {
            return ++seq;
        }
    }
}
