package io.github.sombreknight.feather.dialect;

/**
 * 达梦数据库（DM）方言：信创/政务场景常用国产数据库，SQL 兼容 MySQL / SQL Server 混合语法。
 *
 * <p>特性：双引号标识符、LIMIT/OFFSET 分页（DM 支持）、FOR UPDATE 行锁。</p>
 *
 * @author sombreknight
 */
public class DmDialect extends AbstractDialect {

    public DmDialect() {
        super("DM", "\"", "\"");
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
