package io.github.sombreknight.feather.dialect;

/**
 * PostgreSQL 系方言：PostgreSQL / openGauss / KingbaseES(人大金仓) / PolarDB(PG 模式) /
 * Aurora PostgreSQL / CockroachDB 等。
 *
 * <p>特性：双引号标识符（仅保留字/特殊字符时使用）、LIMIT/OFFSET 分页、FOR UPDATE 行锁。</p>
 *
 * @author sombreknight
 */
public class PostgresDialect extends AbstractDialect {

    public PostgresDialect() {
        super("PostgreSQL", "\"", "\"");
    }

    @Override
    public String limitClause(int limit) {
        return " limit " + limit + " ";
    }

    @Override
    public String limitClause(int skip, int size) {
        return " limit " + size + " offset " + skip + " ";
    }
}
