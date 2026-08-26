package io.github.sombreknight.feather.dialect;

import io.github.sombreknight.feather.exception.FeatherDaoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 各方言的标识符引用、分页、count 包装、锁与索引提示行为
 *
 * @author sombreknight
 */
public class SqlDialectTest {

    // ==================== 标识符引用（三级策略） ====================

    @Test
    public void plainIdentifierNeverQuoted() {
        // 合法普通标识符：所有方言生成一致 SQL，最大化跨库兼容
        for (SqlDialect d : all()) {
            assertEquals("user_name", d.quoteIdentifier("user_name"), d.getName());
            assertEquals("tb_user", d.quoteIdentifier("tb_user"), d.getName());
            assertEquals("u_2", d.quoteIdentifier("u_2"), d.getName());
        }
    }

    @Test
    public void reservedWordQuotedByDialect() {
        assertEquals("`order`", new MySqlDialect().quoteIdentifier("order"));
        assertEquals("\"order\"", new PostgresDialect().quoteIdentifier("order"));
        assertEquals("[order]", new SqlServerDialect().quoteIdentifier("order"));
        assertEquals("\"order\"", new OracleDialect().quoteIdentifier("order"));
        assertEquals("`user`", new MySqlDialect().quoteIdentifier("user")); // PG/SQLServer 关键字
        assertEquals("\"user\"", new H2Dialect().quoteIdentifier("user"));
    }

    @Test
    public void specialCharQuotedByDialect() {
        assertEquals("`weird col`", new MySqlDialect().quoteIdentifier("weird col"));
        assertEquals("\"weird col\"", new PostgresDialect().quoteIdentifier("weird col"));
        assertEquals("[weird col]", new SqlServerDialect().quoteIdentifier("weird col"));
        assertEquals("`9abc`", new MySqlDialect().quoteIdentifier("9abc")); // 数字开头
    }

    @Test
    public void alreadyQuotedPassThrough() {
        for (SqlDialect d : all()) {
            assertEquals("`a`", d.quoteIdentifier("`a`"), d.getName());
            assertEquals("\"a\"", d.quoteIdentifier("\"a\""), d.getName());
            assertEquals("[a]", d.quoteIdentifier("[a]"), d.getName());
        }
        // 大小写保留
        assertEquals("`TbUser`", new MySqlDialect().quoteIdentifier("`TbUser`"));
    }

    // ==================== 分页 ====================

    @Test
    public void limitClauseStyles() {
        // LIMIT/OFFSET 族
        assertEquals(" limit 10 ", new MySqlDialect().limitClause(10));
        assertEquals(" limit 2 offset 20 ", new MySqlDialect().limitClause(20, 2));
        assertEquals(" limit 2 offset 20 ", new PostgresDialect().limitClause(20, 2));
        assertEquals(" limit 2 offset 20 ", new H2Dialect().limitClause(20, 2));
        assertEquals(" limit 2 offset 20 ", new SqliteDialect().limitClause(20, 2));
        assertEquals(" limit 2 offset 20 ", new DmDialect().limitClause(20, 2));
        // OFFSET/FETCH 族
        assertEquals(" offset 20 rows fetch next 2 rows only ", new SqlServerDialect().limitClause(20, 2));
        assertEquals(" offset 20 rows fetch next 2 rows only ", new OracleDialect().limitClause(20, 2));
        assertEquals(" offset 0 rows fetch next 10 rows only ", new SqlServerDialect().limitClause(10));
    }

    @Test
    public void requiresOrderByOnlyForFetchStyle() {
        assertFalse(new MySqlDialect().requiresOrderByForPaging());
        assertFalse(new PostgresDialect().requiresOrderByForPaging());
        assertTrue(new SqlServerDialect().requiresOrderByForPaging());
        assertTrue(new OracleDialect().requiresOrderByForPaging());
    }

    // ==================== count 包装 ====================

    @Test
    public void wrapCountStripsOrderByAndLock() {
        MySqlDialect d = new MySqlDialect();
        String wrapped = d.wrapCount("select user_name from tb_user where age > 18 order by age desc for update");
        assertEquals(" select count(*) from (select user_name from tb_user where age > 18) feather_count ", wrapped);

        // 无 order by 时原样包装
        String plain = d.wrapCount("select * from tb_user");
        assertEquals(" select count(*) from (select * from tb_user) feather_count ", plain);
    }

    @Test
    public void wrapCountKeepsNestedOrderBy() {
        // 子查询内部的 order by 不受影响（括号深度 > 0）
        MySqlDialect d = new MySqlDialect();
        String sql = "select a.id from (select id from tb_user order by id limit 5) a";
        String wrapped = d.wrapCount(sql);
        assertEquals(" select count(*) from (select a.id from (select id from tb_user order by id limit 5) a) feather_count ", wrapped);
    }

    @Test
    public void wrapCountIgnoresLikeKeywordInsideString() {
        // 参数化查询不会把 like 值拼进 SQL，此处验证 'order by' 作为别名/字符串时不受影响（前后无空白边界）
        MySqlDialect d = new MySqlDialect();
        String sql = "select 'order by x' as note from tb_user";
        assertEquals(" select count(*) from (select 'order by x' as note from tb_user) feather_count ", d.wrapCount(sql));
    }

    @Test
    public void stripTailNullAndEmpty() {
        // issue #6：whereSql 为 null 时按无条件处理，不拼出字面量 null
        MySqlDialect d = new MySqlDialect();
        assertEquals("", d.stripTailForCount(null));
        assertEquals("", d.stripTailForCount(""));
        assertEquals("select * from tb_user", d.stripTailForCount("select * from tb_user order by id"));
    }

    // ==================== LIKE 转义（issue #5） ====================

    @Test
    public void likeEscapeClauseUsesPipe() {
        // 统一非反斜杠转义符，规避 MySQL 默认 sql_mode 下 escape '\' 语法错误
        for (SqlDialect d : all()) {
            assertEquals(" escape '|'", d.likeEscapeClause(), d.getName());
        }
    }

    @Test
    public void escapeLikeValueEscapesWildcardsAndPipe() {
        MySqlDialect d = new MySqlDialect();
        assertEquals(null, d.escapeLikeValue(null));
        assertEquals("", d.escapeLikeValue(""));
        assertEquals("张", d.escapeLikeValue("张"));
        assertEquals("50|%|_", d.escapeLikeValue("50%_"));
        assertEquals("a||b", d.escapeLikeValue("a|b"));       // 转义符自身
        assertEquals("a\\b", d.escapeLikeValue("a\\b"));    // 反斜杠在 | 转义符下无特殊含义，原样保留
    }

    // ==================== 锁与索引提示 ====================

    @Test
    public void forUpdateSupport() {
        assertEquals(" for update ", new MySqlDialect().forUpdateClause());
        assertEquals(" for update ", new PostgresDialect().forUpdateClause());
        assertEquals(" for update ", new OracleDialect().forUpdateClause());
        assertNull(new SqliteDialect().forUpdateClause(), "SQLite 单写者无行锁，应忽略");
        assertThrows(FeatherDaoException.class, new SqlServerDialect()::forUpdateClause,
                "SQL Server 不支持 FOR UPDATE 语法，应直接抛异常");
    }

    @Test
    public void forceIndexSupport() {
        assertTrue(new MySqlDialect().supportsForceIndex());
        assertEquals(" force index (idx_user_name)", new MySqlDialect().forceIndexClause("idx_user_name"));
        assertFalse(new PostgresDialect().supportsForceIndex());
        assertThrows(FeatherDaoException.class, () -> new PostgresDialect().forceIndexClause("idx"));
        assertThrows(FeatherDaoException.class, () -> new DefaultDialect().forceIndexClause("idx"));
    }

    private static SqlDialect[] all() {
        return new SqlDialect[]{
                new DefaultDialect(), new MySqlDialect(), new PostgresDialect(),
                new SqlServerDialect(), new OracleDialect(), new SqliteDialect(),
                new H2Dialect(), new DmDialect()
        };
    }
}
