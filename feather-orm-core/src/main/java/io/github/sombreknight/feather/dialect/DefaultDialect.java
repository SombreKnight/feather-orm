package io.github.sombreknight.feather.dialect;

/**
 * 兜底方言：未知数据库的保守策略——普通标识符不引用（最大跨库兼容）、LIMIT/OFFSET 分页。
 *
 * <p>适用于未识别的数据库；分页要求支持 {@code LIMIT size OFFSET skip} 语法。</p>
 *
 * @author sombreknight
 */
public class DefaultDialect extends AbstractDialect {

    public DefaultDialect() {
        super("Default", "\"", "\"");
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
