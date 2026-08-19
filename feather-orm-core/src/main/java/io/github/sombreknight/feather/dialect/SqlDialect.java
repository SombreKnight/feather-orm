package io.github.sombreknight.feather.dialect;

import io.github.sombreknight.feather.exception.FeatherDaoException;

/**
 * SQL 方言 SPI：抽象各关系型数据库在标识符引用、分页、锁、hint 上的语法差异。
 *
 * <p>框架所有 SQL 生成点（ColumnMapper / FieldMeta / QueryHelper / JdbcDAO）都通过
 * 本接口取方言片段，从而做到"一套实体定义，多数据库运行"。内置实现见
 * {@link DialectRegistry}，也可自行实现并注册（stater 中替换 SqlDialect Bean）。</p>
 *
 * <p>分页语法按方言族归类，只有两种写法，覆盖绝大多数主流数据库：</p>
 * <ul>
 *   <li>{@code LIMIT size OFFSET skip}：MySQL / MariaDB / TiDB / OceanBase / PostgreSQL /
 *       openGauss / KingbaseES / SQLite / H2 / 达梦 DM</li>
 *   <li>{@code OFFSET skip ROWS FETCH NEXT size ROWS ONLY}：SQL Server 2012+ / Oracle 12c+</li>
 * </ul>
 *
 * @author sombreknight
 */
public interface SqlDialect {

    /**
     * 方言名称（用于日志与错误提示），如 "MySQL"、"PostgreSQL"
     */
    String getName();

    /**
     * 标识符（表名 / 列名 / 索引名）引用：
     *
     * <ol>
     *   <li>已带引用符（反引号 / 双引号 / 方括号）→ 原样透传，尊重用户显式指定；</li>
     *   <li>合法普通标识符（字母数字下划线、非保留字）→ 不引用，跨库生成一致的 SQL；</li>
     *   <li>含特殊字符或命中保留字 → 按当前方言的引用符包裹。</li>
     * </ol>
     */
    String quoteIdentifier(String identifier);

    /**
     * 无偏移的分页片段（如 {@code limit 10}），用于 limitOne / limit(n) 场景
     */
    String limitClause(int limit);

    /**
     * 分页片段（skip = 偏移量，size = 每页条数），用于 findPageByPageNum 等
     */
    String limitClause(int skip, int size);

    /**
     * 悲观锁子句（追加在查询语句末尾）。返回 null 表示当前方言不支持行锁（如 SQLite），调用方应忽略。
     * 默认 {@code for update}；SQL Server 不支持该语法，其实现直接抛 {@link FeatherDaoException}。
     */
    default String forUpdateClause() {
        return " for update ";
    }

    /**
     * 是否支持 MySQL 系 {@code FORCE INDEX} 表提示
     */
    default boolean supportsForceIndex() {
        return false;
    }

    /**
     * 生成 FORCE INDEX 片段；不支持的方言直接抛 {@link FeatherDaoException}
     */
    default String forceIndexClause(String indexName) {
        throw new FeatherDaoException("数据库[" + getName() + "]不支持 FORCE INDEX，请移除 forceIndex() 或改用原生 SQL");
    }

    /**
     * 分页 count 包装：把查询包成子查询并剥离尾部对 count 无意义的子句（order by / for update），
     * 避免 SQL Server 等数据库对"无 TOP/LIMIT 的子查询 order by"报错。
     */
    default String wrapCount(String sql) {
        return " select count(*) from (" + AbstractDialect.stripTail(sql) + ") feather_count ";
    }

    /**
     * 剥离查询尾部对 count 无意义的子句（最外层 order by / for update）。
     *
     * <p>注意：PostgreSQL 等严格模式数据库对 {@code count(*) ... order by col} 直接报错
     * （聚合列必须出现在 GROUP BY 中），因此实体 count 拼接也必须先剥离。</p>
     */
    default String stripTailForCount(String sql) {
        return AbstractDialect.stripTail(sql);
    }

    /**
     * OFFSET/FETCH 类方言（SQL Server / Oracle）要求分页查询必须先有 ORDER BY，
     * 无排序时框架自动补 {@code order by (select 0)} 以满足语法要求。
     */
    default boolean requiresOrderByForPaging() {
        return false;
    }
}
