package io.github.sombreknight.feather.dialect;

/**
 * SQLite 方言。
 *
 * <p>特性：双引号标识符、LIMIT/OFFSET 分页；不支持 FOR UPDATE（SQLite 单写者，无需显式行锁，
 * 框架自动忽略该子句）。</p>
 *
 * @author sombreknight
 */
public class SqliteDialect extends AbstractDialect {

    public SqliteDialect() {
        super("SQLite", "\"", "\"");
    }

    @Override
    public String limitClause(int limit) {
        return " limit " + limit + " ";
    }

    @Override
    public String limitClause(int skip, int size) {
        return " limit " + size + " offset " + skip + " ";
    }

    @Override
    public String forUpdateClause() {
        return null; // SQLite 单写者，无需行锁
    }
}
