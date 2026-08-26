package io.github.sombreknight.feather.core;

import io.github.sombreknight.feather.dialect.SqlDialect;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.ColumnMapper;
import io.github.sombreknight.feather.mapping.Mapper;
import io.github.sombreknight.feather.util.LambdaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询辅助器：以面向对象的方式拼装 SQL 条件
 *
 * <pre>
 * dao.findList(dao.getQueryHelper()
 *         .whereEqual(UserEntity::getUserName, "张三")
 *         .whereGte(UserEntity::getAge, 18)
 *         .whereContains(UserEntity::getUserName, "张")
 *         .orderByDesc(UserEntity::getCreateTime));
 * </pre>
 *
 * <p>字段名一律使用类型安全的 Lambda 方法引用（{@link FieldFunction}，如 {@code UserEntity::getUserName}），
 * 编译期检查、重构自动跟随；自动映射为数据库列名，找不到映射时立即抛出异常（fail-fast，杜绝 SQL 注入）。</p>
 *
 * <p>动态字段名（运行时变量）场景请使用 {@code JdbcDAO} 原生 SQL。</p>
 *
 * @param <T> 实体类型
 * @author sombreknight
 */
public class QueryHelper<T extends BaseEntity<?>> {

    private static final Logger log = LoggerFactory.getLogger(QueryHelper.class);

    private static final String KEY_WORD_WHERE = " where ";
    private static final String KEY_WORD_AND = " and ";
    private static final String KEY_WORD_ORDER_BY = " order by ";
    private static final String KEY_WORD_GROUP_BY = " group by ";
    private static final String KEY_WORD_ASC = " asc";
    private static final String KEY_WORD_DESC = " desc";
    private static final String KEY_WORD_IN = " in ";
    private static final String KEY_WORD_NOT_IN = " not in ";
    private static final String KEY_WORD_GTE = " >= ";
    private static final String KEY_WORD_GT = " > ";
    private static final String KEY_WORD_LTE = " <= ";
    private static final String KEY_WORD_LT = " < ";
    private static final String KEY_WORD_LIKE = " like ";
    private static final String KEY_WORD_EQUAL = " = ";
    private static final String SEPARATOR_COMMA = ", ";
    private static final String SEPARATOR_LEFT = "(";
    private static final String SEPARATOR_RIGHT = ")";
    private static final String SEPARATOR_COLON = ":";

    private final Class<T> tableClass;
    private final ColumnMapper<T> columnMapper;
    private final SqlDialect dialect;

    private final List<String> selectFieldList = new ArrayList<>(4);
    private final StringBuilder whereBlock = new StringBuilder(" 1=1 ");
    private final StringBuilder groupByBlock = new StringBuilder();
    private final List<String> orderByFieldList = new ArrayList<>(2);
    private final StringBuilder limitBlock = new StringBuilder();

    private final SqlParam sqlParam = SqlParam.create();

    private boolean withTotal = true;
    private boolean withPagination = false;
    private boolean forUpdate = false;
    private boolean forIndexSwitch = false;
    private String forceIndexName;

    private int placeHolderCounter = 0;
    private Integer page = 1;
    private Integer pageSize = 20;

    private final boolean logShowSql;

    public QueryHelper(Class<T> tableClass) {
        this(tableClass, Mapper.getInstance().getDialect(), false);
    }

    public QueryHelper(Class<T> tableClass, boolean logShowSql) {
        this(tableClass, Mapper.getInstance().getDialect(), logShowSql);
    }

    /**
     * 多数据源场景：按指定方言构建（BaseDAO 使用所属 JdbcDAO 的方言，保证列引用与本库一致）
     */
    public QueryHelper(Class<T> tableClass, SqlDialect dialect) {
        this(tableClass, dialect, false);
    }

    public QueryHelper(Class<T> tableClass, SqlDialect dialect, boolean logShowSql) {
        this.tableClass = tableClass;
        this.columnMapper = Mapper.getInstance().getColumnMapper(tableClass, dialect);
        this.dialect = dialect;
        this.logShowSql = logShowSql;
    }

    // ==================== select 片段 ====================

    /**
     * 指定查询列（Java 字段名），支持 "field as alias" 形式
     */
    public QueryHelper<T> selectFields(String... fields) {
        for (String field : fields) {
            selectFieldList.add(resolveSelectField(field));
        }
        return this;
    }

    /**
     * 统计列：count(*)
     */
    public QueryHelper<T> countField() {
        selectFieldList.add("count(*)");
        return this;
    }

    /**
     * 统计列：count(字段)
     */
    public QueryHelper<T> countField(FieldFunction<T, ?> field) {
        selectFieldList.add("count(" + getDbFieldName(LambdaUtils.resolveFieldName(field)) + ")");
        return this;
    }

    // ==================== where 条件 ====================

    /**
     * 等值条件：{@code whereEqual(UserEntity::getUserName, "张三")} → {@code user_name = :user_name_1}
     */
    public QueryHelper<T> whereEqual(FieldFunction<T, ?> field, Object value) {
        String column = getDbFieldName(LambdaUtils.resolveFieldName(field));
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_EQUAL).append(SEPARATOR_COLON).append(key);
        sqlParam.add(key, convertEnum(value));
        return this;
    }

    /**
     * IN 条件：{@code whereIn(UserEntity::getId, idList)}；单元素自动降级为等值查询，空集合忽略
     */
    public <R> QueryHelper<T> whereIn(FieldFunction<T, ?> field, List<R> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        if (values.size() == 1) {
            return whereEqual(field, values.get(0));
        }
        String column = getDbFieldName(LambdaUtils.resolveFieldName(field));
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_IN)
                .append(SEPARATOR_LEFT).append(SEPARATOR_COLON).append(key).append(SEPARATOR_RIGHT);
        sqlParam.add(key, values);
        return this;
    }

    /**
     * NOT IN 条件；空集合忽略
     */
    public <R> QueryHelper<T> whereNotIn(FieldFunction<T, ?> field, List<R> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        String column = getDbFieldName(LambdaUtils.resolveFieldName(field));
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_NOT_IN)
                .append(SEPARATOR_LEFT).append(SEPARATOR_COLON).append(key).append(SEPARATOR_RIGHT);
        sqlParam.add(key, values);
        return this;
    }

    /**
     * 大于条件：{@code whereGt(UserEntity::getAge, 18)}
     */
    public QueryHelper<T> whereGt(FieldFunction<T, ?> field, Object value) {
        return range(field, value, KEY_WORD_GT);
    }

    /**
     * 大于等于条件
     */
    public QueryHelper<T> whereGte(FieldFunction<T, ?> field, Object value) {
        return range(field, value, KEY_WORD_GTE);
    }

    /**
     * 小于条件
     */
    public QueryHelper<T> whereLt(FieldFunction<T, ?> field, Object value) {
        return range(field, value, KEY_WORD_LT);
    }

    /**
     * 小于等于条件
     */
    public QueryHelper<T> whereLte(FieldFunction<T, ?> field, Object value) {
        return range(field, value, KEY_WORD_LTE);
    }

    /**
     * 原生 LIKE：通配符（{@code %} / {@code _}）由调用方自行传入，不转义。
     * 例如 {@code whereLike(UserEntity::getUserName, "张%")}
     */
    public QueryHelper<T> whereLike(FieldFunction<T, ?> field, String keyWord) {
        String column = getDbFieldName(LambdaUtils.resolveFieldName(field));
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_LIKE).append(SEPARATOR_COLON).append(key);
        sqlParam.add(key, keyWord);
        return this;
    }

    /**
     * 包含模糊：{@code whereContains(UserEntity::getUserName, "张")} → {@code LIKE '%张%'}。
     * 自动转义通配符（{@code % _ |}），可直接传用户输入，安全。
     */
    public QueryHelper<T> whereContains(FieldFunction<T, ?> field, String keyword) {
        return likeWithEscape(field, keyword == null ? null : "%" + dialect.escapeLikeValue(keyword) + "%");
    }

    /**
     * 前缀模糊：{@code whereStartsWith(UserEntity::getUserName, "张")} → {@code LIKE '张%'}。
     * 自动转义通配符，可直接传用户输入，安全。
     */
    public QueryHelper<T> whereStartsWith(FieldFunction<T, ?> field, String prefix) {
        return likeWithEscape(field, prefix == null ? null : dialect.escapeLikeValue(prefix) + "%");
    }

    /**
     * 后缀模糊：{@code whereEndsWith(UserEntity::getUserName, "张")} → {@code LIKE '%张'}。
     * 自动转义通配符，可直接传用户输入，安全。
     */
    public QueryHelper<T> whereEndsWith(FieldFunction<T, ?> field, String suffix) {
        return likeWithEscape(field, suffix == null ? null : "%" + dialect.escapeLikeValue(suffix));
    }

    private QueryHelper<T> range(FieldFunction<T, ?> field, Object value, String operator) {
        String column = getDbFieldName(LambdaUtils.resolveFieldName(field));
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(operator).append(SEPARATOR_COLON).append(key);
        sqlParam.add(key, convertEnum(value));
        return this;
    }

    /**
     * 转义 LIKE 通配符（% _ |），配合 ESCAPE '|' 子句使用（见 {@link SqlDialect#escapeLikeValue}）；null 原样返回
     */
    private QueryHelper<T> likeWithEscape(FieldFunction<T, ?> field, String pattern) {
        String column = getDbFieldName(LambdaUtils.resolveFieldName(field));
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_LIKE)
                .append(SEPARATOR_COLON).append(key).append(dialect.likeEscapeClause());
        sqlParam.add(key, pattern);
        return this;
    }

    // ==================== 分组 / 排序 / 分页 ====================

    /**
     * 分组：{@code groupBy(UserEntity::getAge, UserEntity::getStatus)}
     */
    @SafeVarargs
    public final QueryHelper<T> groupBy(FieldFunction<T, ?>... fields) {
        if (fields == null || fields.length == 0) {
            return this;
        }
        List<String> columns = new ArrayList<>(fields.length);
        for (FieldFunction<T, ?> field : fields) {
            columns.add(getDbFieldName(LambdaUtils.resolveFieldName(field)));
        }
        groupByBlock.append(KEY_WORD_GROUP_BY).append(String.join(SEPARATOR_COMMA, columns));
        return this;
    }

    /**
     * 升序排序
     */
    public QueryHelper<T> orderByAsc(FieldFunction<T, ?> field) {
        orderByFieldList.add(getDbFieldName(LambdaUtils.resolveFieldName(field)) + KEY_WORD_ASC);
        return this;
    }

    /**
     * 降序排序
     */
    public QueryHelper<T> orderByDesc(FieldFunction<T, ?> field) {
        orderByFieldList.add(getDbFieldName(LambdaUtils.resolveFieldName(field)) + KEY_WORD_DESC);
        return this;
    }

    /**
     * 指定分页（配合 findPageByPageNum 时 JdbcDAO 自动拼接分页片段）
     */
    public QueryHelper<T> limit(Integer page, Integer pageSize) {
        this.page = page;
        this.pageSize = pageSize;
        limitBlock.append(dialect.limitClause(page, pageSize));
        return this;
    }

    public QueryHelper<T> limit(Integer limit) {
        limitBlock.append(dialect.limitClause(limit));
        return this;
    }

    public QueryHelper<T> limitOne() {
        return limit(1);
    }

    /**
     * 强制走索引（仅 MySQL 系支持，其他方言直接抛异常 fail-fast）
     */
    public QueryHelper<T> forceIndex(String indexName) {
        if (!dialect.supportsForceIndex()) {
            throw new FeatherDaoException("数据库[" + dialect.getName() + "]不支持 FORCE INDEX（仅 MySQL 系支持），请移除 forceIndex()");
        }
        this.forceIndexName = indexName;
        this.forIndexSwitch = true;
        return this;
    }

    /**
     * 悲观锁：SQL Server 等不支持 FOR UPDATE 的方言在此直接抛异常；SQLite 单写者自动忽略
     */
    public QueryHelper<T> forUpdate() {
        dialect.forUpdateClause(); // 提前校验方言支持性（fail-fast）
        this.forUpdate = true;
        return this;
    }

    /**
     * 分页查询是否统计 total
     */
    public QueryHelper<T> withTotal(boolean withTotal) {
        this.withTotal = withTotal;
        return this;
    }

    public QueryHelper<T> withPagination() {
        this.withPagination = true;
        return this;
    }

    // ==================== SQL 输出 ====================

    /**
     * 完整 SQL（select ... from ... where ...）
     */
    public String getSql() {
        String fields = selectFieldList.isEmpty() ? "*" : String.join(SEPARATOR_COMMA, selectFieldList);
        String sql = "select " + fields + " from " + columnMapper.getQuotedTableName() + " jdbc_x " + getWhereSql();
        if (logShowSql) {
            log.info("[Feather] SQL: {}", sql);
        }
        return sql;
    }

    /**
     * where 及之后的片段（供 JdbcDAO 拼接到 from 之后）
     */
    public String getWhereSql() {
        StringBuilder sb = new StringBuilder();
        // force index 必须紧跟表名（from 之后、where 之前），因此放在 where 前面
        if (forIndexSwitch && forceIndexName != null) {
            sb.append(dialect.forceIndexClause(forceIndexName));
        }
        sb.append(KEY_WORD_WHERE).append(whereBlock);
        if (groupByBlock.length() > 0) {
            sb.append(groupByBlock);
        }
        if (!orderByFieldList.isEmpty()) {
            sb.append(KEY_WORD_ORDER_BY).append(String.join(SEPARATOR_COMMA, orderByFieldList));
        }
        if (limitBlock.length() > 0 && !withPagination) {
            // 分页查询时由 JdbcDAO 统一拼接 limit
            sb.append(limitBlock);
        }
        if (forUpdate) {
            String lockClause = dialect.forUpdateClause();
            if (lockClause != null) {
                sb.append(lockClause);
            }
        }
        String sql = sb.toString();
        if (logShowSql) {
            log.info("[Feather] WhereSQL: {}", sql);
        }
        return sql;
    }

    // ==================== 内部方法 ====================

    /**
     * Java 字段名 → 数据库列名（fail-fast，找不到映射立即抛异常）
     */
    private String getDbFieldName(String field) {
        String fieldName = field == null ? "" : field.trim();
        if (fieldName.isEmpty()) {
            throw new FeatherDaoException("查询条件字段名不能为空");
        }
        String alias = null;
        int asIndex = lowerIndexOf(fieldName, " as ");
        if (asIndex >= 0) {
            alias = fieldName.substring(asIndex + 4).trim();
            fieldName = fieldName.substring(0, asIndex).trim();
        }
        String column = columnMapper.getColumn(fieldName);
        if (column == null) {
            throw new FeatherDaoException("字段[" + fieldName + "]在实体[" + tableClass.getName() + "]中不存在或未映射");
        }
        return alias == null ? column : column + " as " + alias;
    }

    /**
     * 解析 select 片段：普通字段或 "field as alias"
     */
    private String resolveSelectField(String field) {
        String fieldName = field == null ? "" : field.trim();
        if (fieldName.isEmpty()) {
            throw new FeatherDaoException("select 字段名不能为空");
        }
        String alias = null;
        int asIndex = lowerIndexOf(fieldName, " as ");
        if (asIndex >= 0) {
            alias = fieldName.substring(asIndex + 4).trim();
            fieldName = fieldName.substring(0, asIndex).trim();
        }
        String column = columnMapper.getColumn(fieldName);
        if (column == null) {
            throw new FeatherDaoException("字段[" + fieldName + "]在实体[" + tableClass.getName() + "]中不存在或未映射");
        }
        return alias == null ? column : column + " as " + alias;
    }

    private static int lowerIndexOf(String source, String keyword) {
        return source.toLowerCase().indexOf(keyword);
    }

    /**
     * 同名占位符冲突规避：每个条件生成唯一参数名
     */
    private String newPlaceKey(String column) {
        placeHolderCounter++;
        return column + "_" + placeHolderCounter;
    }

    private Object convertEnum(Object value) {
        if (value instanceof io.github.sombreknight.feather.type.CodeEnum) {
            return ((io.github.sombreknight.feather.type.CodeEnum<?>) value).getValue();
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return value;
    }

    // ==================== 访问器 ====================

    public SqlParam getSqlParam() {
        return sqlParam;
    }

    public boolean isWithTotal() {
        return withTotal;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getPageSize() {
        return pageSize;
    }
}
