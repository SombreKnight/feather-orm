package io.github.sombreknight.feather.dialect;

/**
 * Oracle 方言：Oracle 12c+（含 12.1 引入的 OFFSET/FETCH 分页语法；11g 及更早不支持，请升级或手写 ROWNUM 分页）。
 *
 * <p>特性：双引号标识符（普通标识符不引用，依赖 Oracle 默认大写折叠，最安全）、
 * OFFSET/FETCH 分页（必须配合 ORDER BY，框架自动补 {@code order by (select 0)}）、FOR UPDATE 行锁。</p>
 *
 * @author sombreknight
 */
public class OracleDialect extends AbstractDialect {

    public OracleDialect() {
        super("Oracle", "\"", "\"");
    }

    @Override
    public String limitClause(int limit) {
        return " offset 0 rows fetch next " + limit + " rows only ";
    }

    @Override
    public String limitClause(int skip, int size) {
        return " offset " + skip + " rows fetch next " + size + " rows only ";
    }

    @Override
    public boolean requiresOrderByForPaging() {
        return true;
    }
}
