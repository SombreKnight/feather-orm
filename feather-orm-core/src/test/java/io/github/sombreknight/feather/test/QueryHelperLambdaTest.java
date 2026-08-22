package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.core.QueryHelper;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.MySqlDialect;
import io.github.sombreknight.feather.exception.FeatherDaoException;
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
 * QueryHelper Lambda 字段引用（FieldFunction）测试（无需数据库）
 *
 * <p>验证 Lambda 方法引用形式的 SQL 生成与字符串版完全一致，
 * 以及父类字段、boolean is 前缀 getter、非法引用 fail-fast 等边界。</p>
 *
 * @author sombreknight
 */
public class QueryHelperLambdaTest {

    @Table("tb_lambda_test")
    public static class LambdaTestEntity extends BaseEntity<Long> {
        private boolean active;

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        /** 非法引用目标：静态方法无 get/is/set 前缀 */
        public static String staticHelper(LambdaTestEntity t) {
            return "x";
        }
    }

    @BeforeAll
    public static void setup() {
        Mapper.getInstance().setDialect(new MySqlDialect());
    }

    @AfterAll
    public static void tearDown() {
        Mapper.getInstance().setDialect(DialectRegistry.defaultDialect());
    }

    @Test
    public void whereEqualLambdaMapsFieldToColumn() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereEqual(UserEntity::getUserName, "张三").getWhereSql();
        assertTrue(sql.contains("user_name = :user_name_1"));
        assertFalse(sql.contains("userName"), "SQL 中不应出现 Java 字段名");
    }

    @Test
    public void columnOverrideAnnotation() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereEqual(UserEntity::getPhone, "138").getWhereSql();
        assertTrue(sql.contains("phone_no = :phone_no_1"));
    }

    @Test
    public void parentClassField() {
        // getter 定义在 BaseEntity（父类），解析仍走实体自身的 ColumnMapper
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereEqual(UserEntity::getId, 1L).getWhereSql();
        assertTrue(sql.contains("id = :id_1"));
    }

    @Test
    public void booleanIsPrefixGetter() {
        QueryHelper<LambdaTestEntity> qh = new QueryHelper<>(LambdaTestEntity.class);
        String sql = qh.whereEqual(LambdaTestEntity::isActive, true).getWhereSql();
        assertTrue(sql.contains("active = :active_1"));
    }

    @Test
    public void sameFieldRangeUsesDistinctPlaceholders() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereGte(UserEntity::getAge, 18).whereLt(UserEntity::getAge, 60).getWhereSql();
        assertTrue(sql.contains("age >= :age_1"));
        assertTrue(sql.contains("age < :age_2"));
    }

    @Test
    public void whereInSingleElementDegradesToEqual() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereIn(UserEntity::getId, Collections.singletonList(1L)).getWhereSql();
        assertTrue(sql.contains("id = :id_1"));
        assertFalse(sql.contains("in ("), "单元素 in 应降级为等值查询");
    }

    @Test
    public void whereInMultiValues() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereIn(UserEntity::getId, Arrays.asList(1L, 2L, 3L)).getWhereSql();
        assertTrue(sql.contains("id in (:id_1)"));
    }

    @Test
    public void whereNotIn() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereNotIn(UserEntity::getId, Arrays.asList(1L, 2L)).getWhereSql();
        assertTrue(sql.contains("id not in (:id_1)"));
    }

    @Test
    public void whereLike() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereLike(UserEntity::getUserName, "张%").getWhereSql();
        assertTrue(sql.contains("user_name like :user_name_1"));
    }

    @Test
    public void orderByAscAndDesc() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.orderByAsc(UserEntity::getAge).orderByDesc(UserEntity::getId).getWhereSql();
        assertTrue(sql.contains("order by age asc, id desc"));
    }

    @Test
    public void groupByMultipleFields() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.groupBy(UserEntity::getAge, UserEntity::getStatus).getWhereSql();
        assertTrue(sql.contains("group by age, status"));
    }

    @Test
    public void countField() {
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.countField(UserEntity::getId).getSql();
        assertTrue(sql.contains("count(id)"));
    }

    @Test
    public void nonGetterReferenceThrows() {
        // 静态方法引用无法推导字段名，fail-fast
        QueryHelper<LambdaTestEntity> qh = new QueryHelper<>(LambdaTestEntity.class);
        assertThrows(FeatherDaoException.class, () -> qh.whereEqual(LambdaTestEntity::staticHelper, "x"));
    }

    @Test
    public void nullReferenceThrows() {
        // 唯一重载下 null 不再有编译歧义，直接进入 FieldFunction 解析并 fail-fast
        QueryHelper<LambdaTestEntity> qh = new QueryHelper<>(LambdaTestEntity.class);
        assertThrows(FeatherDaoException.class, () -> qh.whereEqual(null, "x"));
    }
}
