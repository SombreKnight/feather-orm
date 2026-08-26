package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.QueryHelper;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.MySqlDialect;
import io.github.sombreknight.feather.mapping.Mapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QueryHelper SQL 生成单元测试（无需数据库）
 *
 * <p>本类验证 MySQL 方言下的 SQL 片段生成（反引号、force index、limit 等）；
 * Lambda 字段引用（FieldFunction）专项见 {@link QueryHelperLambdaTest}。</p>
 *
 * @author sombreknight
 */
public class QueryHelperTest {

    @BeforeAll
    public static void setup() {
        Mapper.getInstance().setDialect(new MySqlDialect());
    }

    @AfterAll
    public static void tearDown() {
        Mapper.getInstance().setDialect(DialectRegistry.defaultDialect());
    }

    @Test
    public void defaultSql() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.getSql();
        // 最小引用策略：普通标识符不引用（跨库一致），保留字/特殊字符才按方言引用
        assertTrue(sql.contains("select * from tb_user jdbc_x"));
        assertTrue(sql.contains("where  1=1"));
    }

    @Test
    public void whereEqualMapsFieldToColumn() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereEqual(UserEntity::getUserName, "张三").getWhereSql();
        assertTrue(sql.contains("user_name = :user_name_1"));
        assertFalse(sql.contains("userName"), "SQL 中不应出现 Java 字段名");

        // @Column 覆盖
        QueryHelper<UserEntity> qh2 = new QueryHelper<>(UserEntity.class);
        String sql2 = qh2.whereEqual(UserEntity::getPhone, "138").getWhereSql();
        assertTrue(sql2.contains("phone_no = :phone_no_1"));
    }

    @Test
    public void sameFieldRangeUsesDistinctPlaceholders() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereGt(UserEntity::getAge, 18).whereLt(UserEntity::getAge, 60).getWhereSql();
        assertTrue(sql.contains("age > :age_1"));
        assertTrue(sql.contains("age < :age_2"));
    }

    @Test
    public void whereInSingleElementDegradesToEqual() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereIn(UserEntity::getId, Collections.singletonList(1L)).getWhereSql();
        assertTrue(sql.contains("id = :id_1"));
        assertFalse(sql.contains("in ("), "单元素 in 应降级为等值查询");

        QueryHelper<UserEntity> qh2 = new QueryHelper<>(UserEntity.class);
        String sql2 = qh2.whereIn(UserEntity::getId, Arrays.asList(1L, 2L, 3L)).getWhereSql();
        assertTrue(sql2.contains("id in (:id_1)"));
    }

    @Test
    public void whereNotInLike() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereNotIn(UserEntity::getId, Arrays.asList(1L, 2L))
                .whereLike(UserEntity::getUserName, "张%").getWhereSql();
        assertTrue(sql.contains("id not in (:id_1)"));
        assertTrue(sql.contains("user_name like :user_name_2"));
    }

    @Test
    public void likeConvenienceMethodsEscapeWildcards() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        // 含通配符的用户输入自动转义，并拼 ESCAPE 子句（统一使用非反斜杠转义符 |，issue #5）
        String sql = qh.whereContains(UserEntity::getUserName, "50%_")
                .whereStartsWith(UserEntity::getUserName, "张")
                .whereEndsWith(UserEntity::getUserName, "三")
                .getWhereSql();
        assertTrue(sql.contains("user_name like :user_name_1 escape '|'"), sql);
        assertTrue(sql.contains("user_name like :user_name_2 escape '|'"), sql);
        assertTrue(sql.contains("user_name like :user_name_3 escape '|'"), sql);
        Object first = qh.getSqlParam().toMap().values().iterator().next();
        assertTrue("%50|%|_%".equals(first), "通配符应被转义: " + first);
    }

    @Test
    public void likeEscapePipeChar() {
        // 转义符自身 | 需转义为 ||，避免误吞后续通配符
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereContains(UserEntity::getUserName, "a|b%c").getWhereSql();
        Object param = qh.getSqlParam().toMap().values().iterator().next();
        assertTrue("%a||b|%c%".equals(param), "| 与 % 应同时转义: " + param);
        assertTrue(sql.contains("escape '|'"));
    }

    @Test
    public void selectFieldsAndAlias() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.selectFields("userName", "age").getSql();
        assertTrue(sql.contains("select user_name, age from tb_user"));

        QueryHelper<UserEntity> qh2 = new QueryHelper<>(UserEntity.class);
        String sql2 = qh2.selectFields("userName as u").getSql();
        assertTrue(sql2.contains("select user_name as u from tb_user"));
    }

    @Test
    public void countField() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        assertTrue(qh.countField().getSql().contains("select count(*) from"));
        QueryHelper<UserEntity> qh2 = new QueryHelper<>(UserEntity.class);
        assertTrue(qh2.countField(UserEntity::getId).getSql().contains("select count(id) from"));
    }

    @Test
    public void groupByOrderByLimitForUpdate() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.groupBy(UserEntity::getAge).orderByAsc(UserEntity::getAge)
                .orderByDesc(UserEntity::getId).limit(10).forUpdate().getWhereSql();
        assertTrue(sql.contains("group by age"));
        assertTrue(sql.contains("order by age asc, id desc"));
        assertTrue(sql.contains("limit 10"));
        assertTrue(sql.contains("for update"));
    }

    @Test
    public void limitOne() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        assertTrue(qh.limitOne().getWhereSql().contains("limit 1"));
    }

    @Test
    public void forceIndexBeforeWhere() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.forceIndex("idx_user_name").whereEqual(UserEntity::getUserName, "x").getWhereSql();
        assertTrue(sql.startsWith(" force index (idx_user_name) where"), "force index 必须紧跟表名之后: " + sql);
    }

    @Test
    public void withPaginationSuppressesManualLimit() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        qh.limit(2, 10).withPagination();
        assertFalse(qh.getWhereSql().contains("limit"), "分页查询时 limit 由 JdbcDAO 拼接");
    }

    @Test
    public void unknownSelectFieldFailsFast() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        // 字符串入口仅剩 selectFields（alias 能力），未知字段仍 fail-fast
        assertThrows(Exception.class, () -> qh.selectFields("notExistField"));
    }

    @Test
    public void enumParamConvertedToCode() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        qh.whereEqual(UserEntity::getStatus, OrderStatus.PAID);
        Object value = qh.getSqlParam().toMap().values().iterator().next();
        assertTrue(Integer.valueOf(2).equals(value), "CodeEnum 条件参数应转换为业务码: " + value);
    }
}
