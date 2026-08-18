package io.github.sombreknight.feather.test;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.PagingResult;
import io.github.sombreknight.feather.core.SqlParam;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcDAO 低层 API 测试：指定 id、批量分组、无字段更新、DTO 映射、字段分页、分区查询、fail-fast
 *
 * @author sombreknight
 */
public class JdbcDAOAdvancedTest {

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;
    private static long idSequence = 1;

    @BeforeAll
    public static void init() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:feather-jdbcdao;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        new JdbcTemplate(dataSource).execute("CREATE TABLE tb_item (" +
                "id BIGINT PRIMARY KEY," +
                "name VARCHAR(64)," +
                "price DECIMAL(12,2)," +
                "note VARCHAR(128)" +
                ")");

        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        RowMapperSupport support = new RowMapperSupport(registry, new JavassistRowMapperFactory());
        jdbcDAO = new JdbcDAO(new NamedParameterJdbcTemplate(dataSource), new SeqIdGenerator(), support, 0);
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    public void clean() {
        new JdbcTemplate(dataSource).execute("DELETE FROM tb_item");
    }

    // ==================== 保存 ====================

    @Test
    public void saveWithUserSpecifiedId() {
        ItemEntity item = newItem("指定id", new BigDecimal("1.00"));
        item.setId(9001L);
        assertEquals(1, jdbcDAO.save(item));

        ItemEntity found = jdbcDAO.findById(ItemEntity.class, 9001L);
        assertNotNull(found);
        assertEquals("指定id", found.getName());
    }

    @Test
    public void saveNullThrows() {
        assertThrows(FeatherDaoException.class, () -> jdbcDAO.save(null));
    }

    @Test
    public void saveBatchGroupsByNonNullColumns() {
        ItemEntity a = newItem("A", new BigDecimal("10.00"));
        a.setNote("有备注");
        ItemEntity b = newItem("B", new BigDecimal("20.00"));
        b.setNote(null); // 与 a 的非空列集合不同 → 分成两组批量执行

        int[] results = jdbcDAO.saveBatch(java.util.Arrays.asList(a, b));
        assertEquals(2, results.length);

        ItemEntity foundA = jdbcDAO.findById(ItemEntity.class, a.getId());
        ItemEntity foundB = jdbcDAO.findById(ItemEntity.class, b.getId());
        assertEquals("有备注", foundA.getNote());
        assertNull(foundB.getNote());
    }

    // ==================== 更新 ====================

    @Test
    public void updateWithNoNonNullFieldsReturnsZero() {
        ItemEntity item = newItem("原名称", new BigDecimal("5.00"));
        jdbcDAO.save(item);

        ItemEntity onlyId = new ItemEntity();
        onlyId.setId(item.getId());
        assertEquals(0, jdbcDAO.update(onlyId), "无可更新字段应返回 0 且不报错");

        ItemEntity found = jdbcDAO.findById(ItemEntity.class, item.getId());
        assertEquals("原名称", found.getName());
    }

    @Test
    public void updateNullIdThrows() {
        assertThrows(FeatherDaoException.class, () -> jdbcDAO.update(new ItemEntity()));
    }

    // ==================== DTO 查询 ====================

    @Test
    public void findDtoListSkipsMissingColumns() {
        ItemEntity item = newItem("DTO条目", new BigDecimal("88.50"));
        jdbcDAO.save(item);

        List<ItemDTO> list = jdbcDAO.findDtoList(ItemDTO.class,
                " select name, price from `tb_item` where id = :id ",
                SqlParam.create("id", item.getId()));
        assertEquals(1, list.size());
        assertEquals("DTO条目", list.get(0).getName());
        assertEquals(0, new BigDecimal("88.50").compareTo(list.get(0).getPrice()));
        assertNull(list.get(0).getNotInResult(), "查询结果不存在的列应跳过");
    }

    @Test
    public void findDtoSingleAndPage() {
        for (int i = 0; i < 3; i++) {
            jdbcDAO.save(newItem("DTO分页" + i, new BigDecimal("10" + i + ".00")));
        }
        ItemDTO single = jdbcDAO.findDto(ItemDTO.class,
                " select name, price from `tb_item` where name = :name ",
                SqlParam.create("name", "DTO分页0"));
        assertNotNull(single);
        assertEquals("DTO分页0", single.getName());

        PagingResult<ItemDTO> page = jdbcDAO.findDtoPageByPageNum(ItemDTO.class,
                " select name, price from `tb_item` ",
                null, 1, 2, true);
        assertEquals(3, page.getPageInfo().getTotal());
        assertEquals(2, page.getData().size());
    }

    // ==================== 字段查询与分页 ====================

    @Test
    public void findFieldAndFieldPage() {
        jdbcDAO.save(newItem("字段A", new BigDecimal("1.00")));
        jdbcDAO.save(newItem("字段B", new BigDecimal("2.00")));

        List<String> names = jdbcDAO.findFieldList(String.class,
                " select name from `tb_item` order by name ", null);
        assertEquals(java.util.Arrays.asList("字段A", "字段B"), names);

        PagingResult<String> page = jdbcDAO.findFieldPageByPageNum(String.class,
                " select name from `tb_item` ", null, 1, 1, true);
        assertEquals(2, page.getPageInfo().getTotal());
        assertEquals(1, page.getData().size());
    }

    // ==================== 主键批量查询（分区） ====================

    @Test
    public void findByIdsPartitionsOver100() {
        List<ItemEntity> items = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            items.add(newItem("批量" + i, new BigDecimal("1.00")));
        }
        jdbcDAO.saveBatch(items);

        List<Long> ids = new ArrayList<>();
        for (ItemEntity item : items) {
            ids.add(item.getId());
        }
        List<ItemEntity> found = jdbcDAO.findByIds(ItemEntity.class, ids);
        assertEquals(101, found.size());
    }

    // ==================== fail-fast ====================

    @Test
    public void nullParamFailsFast() {
        assertThrows(FeatherDaoException.class,
                () -> jdbcDAO.findOne(ItemEntity.class, " where id = :id ", SqlParam.create("id", null)));
    }

    @Test
    public void findListWithoutWhereThrows() {
        assertThrows(FeatherDaoException.class, () -> jdbcDAO.findList(ItemEntity.class, "", null));
    }

    // ==================== 工具 ====================

    private static ItemEntity newItem(String name, BigDecimal price) {
        ItemEntity item = new ItemEntity();
        item.setName(name);
        item.setPrice(price);
        return item;
    }

    /**
     * 顺序递增 id（避免与其它测试类的 FixedIdGenerator 耦合）
     */
    static class SeqIdGenerator implements IdGenerator {
        @Override
        public synchronized long nextId() {
            return idSequence++;
        }
    }
}
