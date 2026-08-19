package io.github.sombreknight.feather.dialect;

/**
 * MySQL 系方言：MySQL / MariaDB / TiDB / Percona / OceanBase(MySQL 模式) / PolarDB(MySQL 模式) 等。
 *
 * <p>特性：反引号标识符（仅保留字/特殊字符时使用）、LIMIT/OFFSET 分页、
 * FORCE INDEX 表提示、FOR UPDATE 行锁。</p>
 *
 * @author sombreknight
 */
public class MySqlDialect extends AbstractDialect {

    public MySqlDialect() {
        super("MySQL", "`", "`");
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
    public boolean supportsForceIndex() {
        return true;
    }

    @Override
    public String forceIndexClause(String indexName) {
        return " force index (" + indexName + ")";
    }
}
