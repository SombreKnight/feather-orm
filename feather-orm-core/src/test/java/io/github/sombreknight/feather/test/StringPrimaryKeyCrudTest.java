package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.UuidIdGenerator;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.ColumnMapper;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.Mapper;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * String（UUID）主键实体 CRUD 集成测试（H2）
 *
 * <p>验证 {@code BaseEntity&lt;String&gt;} 泛型化后的全链路：主键自动生成（UuidIdGenerator）、
 * 主键类型按泛型参数还原（非 Object）、手动指定 id、findById/findByIds/update/delete、分页，以及多层继承的泛型解析。</p>
 *
 * @author sombreknight
 */
public class StringPrimaryKeyCrudTest {

    // ==================== 测试实体 ====================

    @Table("tb_uuid_item")
    public static class UuidItemEntity extends BaseEntity<String> {
        private String name;
        private Integer quantity;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    /** 多层继承：BaseEntity<ID> → MidEntity<ID> → DeepEntity（ID 具体化为 String） */
    public static abstract class MidEntity<ID> extends BaseEntity<ID> {
    }

    @Table("tb_deep_item")
    public static class DeepItemEntity extends MidEntity<String> {
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    /** Long 主键实体（用于验证生成器按主键类型自动匹配） */
    @Table("tb_long_item")
    public static class LongItemEntity extends BaseEntity<Long> {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** Long 顺序生成器（区别于 UUID 生成器，验证按类型路由） */
    public static class SeqLongIdGenerator implements io.github.sombreknight.feather.core.IdGenerator<Long> {
        private long seq = 0;

        @Override
        public Long nextId() {
            return ++seq;
        }

        @Override
        public Class<Long> idType() {
            return Long.class;
        }
    }

    // ==================== 环境 ====================

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;

    @BeforeAll
    public static void init() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:feather-uuid;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE tb_uuid_item (" +
                "id VARCHAR(64) PRIMARY KEY," +
                "name VARCHAR(64)," +
                "quantity INT" +
                ")");
        jdbcTemplate.execute("CREATE TABLE tb_deep_item (" +
                "id VARCHAR(64) PRIMARY KEY," +
                "label VARCHAR(64)" +
                ")");
        jdbcTemplate.execute("CREATE TABLE tb_long_item (" +
                "id BIGINT PRIMARY KEY," +
                "name VARCHAR(64)" +
                ")");

        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        RowMapperSupport support = new RowMapperSupport(registry, new JavassistRowMapperFactory());

        // 同时注册 UUID 与 Long 生成器：按实体主键类型自动匹配
        jdbcDAO = new JdbcDAO(namedParameterJdbcTemplate,
                Arrays.<io.github.sombreknight.feather.core.IdGenerator<?>>asList(
                        new UuidIdGenerator(), new SeqLongIdGenerator()), support, 0);
    }

    @BeforeEach
    public void cleanTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DELETE FROM tb_uuid_item");
        jdbcTemplate.execute("DELETE FROM tb_deep_item");
        jdbcTemplate.execute("DELETE FROM tb_long_item");
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    // ==================== 用例 ====================

    @Test
    public void saveAutoGeneratesUuid() {
        UuidItemEntity item = new UuidItemEntity();
        item.setName("u-1");
        item.setQuantity(3);

        assertEquals(1, jdbcDAO.save(item));
        assertNotNull(item.getId());
        // 生成的是 UUID 字符串
        UUID.fromString(item.getId());

        UuidItemEntity found = jdbcDAO.findById(UuidItemEntity.class, item.getId());
        assertNotNull(found);
        assertEquals("u-1", found.getName());
        assertEquals(3, found.getQuantity());
    }

    @Test
    public void saveWithManualId() {
        UuidItemEntity item = new UuidItemEntity();
        String manualId = UUID.randomUUID().toString();
        item.setId(manualId);
        item.setName("manual");

        assertEquals(1, jdbcDAO.save(item));

        UuidItemEntity found = jdbcDAO.findById(UuidItemEntity.class, manualId);
        assertNotNull(found);
        assertEquals("manual", found.getName());
    }

    @Test
    public void findByIdMissingReturnsNull() {
        assertNull(jdbcDAO.findById(UuidItemEntity.class, UUID.randomUUID().toString()));
    }

    @Test
    public void findByIds() {
        UuidItemEntity a = new UuidItemEntity();
        a.setName("a");
        jdbcDAO.save(a);
        UuidItemEntity b = new UuidItemEntity();
        b.setName("b");
        jdbcDAO.save(b);

        List<UuidItemEntity> list = jdbcDAO.findByIds(UuidItemEntity.class,
                Arrays.asList(a.getId(), b.getId()));
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(i -> "a".equals(i.getName())));
        assertTrue(list.stream().anyMatch(i -> "b".equals(i.getName())));
    }

    @Test
    public void findByIdsEmpty() {
        List<UuidItemEntity> list = jdbcDAO.findByIds(UuidItemEntity.class, Collections.emptyList());
        assertTrue(list.isEmpty());
    }

    @Test
    public void updateNonNullFields() {
        UuidItemEntity item = new UuidItemEntity();
        item.setName("old");
        item.setQuantity(1);
        jdbcDAO.save(item);

        UuidItemEntity patch = new UuidItemEntity();
        patch.setId(item.getId());
        patch.setQuantity(9); // name 不触碰

        assertEquals(1, jdbcDAO.update(patch));

        UuidItemEntity found = jdbcDAO.findById(UuidItemEntity.class, item.getId());
        assertEquals("old", found.getName());
        assertEquals(9, found.getQuantity());
    }

    @Test
    public void deleteEntity() {
        UuidItemEntity item = new UuidItemEntity();
        item.setName("del");
        jdbcDAO.save(item);

        assertEquals(1, jdbcDAO.deleteEntity(UuidItemEntity.class, item));
        assertNull(jdbcDAO.findById(UuidItemEntity.class, item.getId()));
    }

    @Test
    public void findPage() {
        for (int i = 1; i <= 5; i++) {
            UuidItemEntity item = new UuidItemEntity();
            item.setName("p-" + i);
            jdbcDAO.save(item);
        }
        io.github.sombreknight.feather.core.PagingResult<UuidItemEntity> page =
                jdbcDAO.findPageByPageNum(UuidItemEntity.class, " where 1=1 ", null, 1, 2, true);
        assertEquals(5, page.getPageInfo().getTotal());
        assertEquals(2, page.getData().size());
    }

    // ==================== 泛型与匹配 ====================

    @Test
    public void multiLevelInheritanceResolvesStringPk() {
        // 多层继承后主键类型仍还原为 String（非 Object）
        ColumnMapper<DeepItemEntity> mapper =
                Mapper.getInstance().getColumnMapper(DeepItemEntity.class);
        assertEquals(String.class, mapper.getPkType());

        DeepItemEntity deep = new DeepItemEntity();
        deep.setLabel("deep");
        assertEquals(1, jdbcDAO.save(deep)); // 自动匹配 UuidIdGenerator
        assertNotNull(deep.getId());
        UUID.fromString(deep.getId());

        DeepItemEntity found = jdbcDAO.findById(DeepItemEntity.class, deep.getId());
        assertNotNull(found);
        assertEquals("deep", found.getLabel());
    }

    @Test
    public void generatorRoutedByPkType() {
        // String 主键 → UUID 生成器
        UuidItemEntity uuidItem = new UuidItemEntity();
        uuidItem.setName("uuid-routed");
        jdbcDAO.save(uuidItem);
        UUID.fromString(uuidItem.getId());

        // Long 主键 → SeqLongIdGenerator（递增）
        LongItemEntity longItem = new LongItemEntity();
        longItem.setName("long-routed");
        jdbcDAO.save(longItem);
        assertEquals(1L, longItem.getId());
        LongItemEntity longItem2 = new LongItemEntity();
        longItem2.setName("long-routed-2");
        jdbcDAO.save(longItem2);
        assertEquals(2L, longItem2.getId());

        LongItemEntity found = jdbcDAO.findById(LongItemEntity.class, 2L);
        assertNotNull(found);
        assertEquals("long-routed-2", found.getName());
    }

    @Test
    public void saveFailsFastWhenNoMatchingGenerator() {
        // 只注册 Long 生成器时，String 主键实体保存应 fail-fast
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        RowMapperSupport support = new RowMapperSupport(registry, new JavassistRowMapperFactory());
        JdbcDAO uuidLess = new JdbcDAO(template,
                Collections.<io.github.sombreknight.feather.core.IdGenerator<?>>singletonList(
                        new SeqLongIdGenerator()), support, 0);

        UuidItemEntity item = new UuidItemEntity();
        item.setName("no-gen");
        FeatherDaoException ex = assertThrows(FeatherDaoException.class, () -> uuidLess.save(item));
        assertTrue(ex.getMessage().contains("没有匹配的 IdGenerator"));
    }
}
