package io.github.sombreknight.feather.dialect;

import io.github.sombreknight.feather.exception.FeatherDaoException;

/**
 * SQL Server 系方言：SQL Server 2012+ / Azure SQL / Sybase 等。
 *
 * <p>特性：方括号标识符、OFFSET/FETCH 分页（必须配合 ORDER BY，框架自动补
 * {@code order by (select 0)}）、不支持 FOR UPDATE（SQL Server 用表提示 WITH (UPDLOCK)，请手写 SQL）。</p>
 *
 * @author sombreknight
 */
public class SqlServerDialect extends AbstractDialect {

    public SqlServerDialect() {
        super("SQL Server", "[", "]");
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
    public String forUpdateClause() {
        throw new FeatherDaoException(
                "数据库[SQL Server]不支持 FOR UPDATE 语法，请改用表提示（WITH (UPDLOCK)）或手写 SQL");
    }

    @Override
    public boolean requiresOrderByForPaging() {
        return true;
    }
}
