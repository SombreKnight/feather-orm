package io.github.sombreknight.feather.dialect;

/**
 * H2 方言（嵌入式/内存库，也适用于 HSQLDB 等 JVM 嵌入式库的常见用法）。
 *
 * <p>特性：双引号标识符、LIMIT/OFFSET 分页、FOR UPDATE 行锁。</p>
 *
 * @author sombreknight
 */
public class H2Dialect extends AbstractDialect {

    public H2Dialect() {
        super("H2", "\"", "\"");
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
