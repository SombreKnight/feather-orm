package io.github.sombreknight.feather.dialect;

import io.github.sombreknight.feather.core.QueryHelper;
import io.github.sombreknight.feather.mapping.ColumnMapper;
import io.github.sombreknight.feather.mapping.Mapper;
import io.github.sombreknight.feather.test.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局方言切换：setDialect 后已缓存的映射与 SQL 生成随方言重建
 *
 * @author sombreknight
 */
public class MapperDialectTest {

    @AfterEach
    public void reset() {
        Mapper.getInstance().setDialect(DialectRegistry.defaultDialect());
    }

    @Test
    public void columnMapperFollowsDialectSwitch() {
        Mapper.getInstance().setDialect(new MySqlDialect());
        ColumnMapper<UserEntity> mysqlMapper = Mapper.getInstance().getColumnMapper(UserEntity.class);
        // 普通标识符不引用（跨库一致）
        assertEquals("id", mysqlMapper.getQuotedIdColumn());
        assertEquals("tb_user", mysqlMapper.getQuotedTableName());

        // 切换方言后缓存重建，映射仍可用且一致
        Mapper.getInstance().setDialect(new PostgresDialect());
        ColumnMapper<UserEntity> pgMapper = Mapper.getInstance().getColumnMapper(UserEntity.class);
        assertEquals("id", pgMapper.getQuotedIdColumn());
        assertEquals("tb_user", pgMapper.getQuotedTableName());
    }

    @Test
    public void queryHelperUsesCurrentDialect() {
        Mapper.getInstance().setDialect(new PostgresDialect());
        QueryHelper<UserEntity> qh = new QueryHelper<>(UserEntity.class);
        String sql = qh.whereEqual("userName", "张三").limit(2, 10).getSql();
        // PG 方言：表名不引用 + LIMIT size OFFSET skip
        assertTrue(sql.contains("from tb_user jdbc_x"), sql);
        assertTrue(sql.contains("limit 10 offset 2"), sql);
        assertTrue(sql.contains("user_name = :user_name_1"), sql);
    }
}
