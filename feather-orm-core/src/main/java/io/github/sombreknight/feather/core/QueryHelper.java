package io.github.sombreknight.feather.core;

import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.ColumnMapper;
import io.github.sombreknight.feather.mapping.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询辅助器：以面向对象的方式拼装 SQL 条件
 *
 * <pre>
 * dao.findList(dao.getQueryHelper()
 *         .whereEqual("userName", "张三")
 *         .whereGte("age", 18)
 *         .orderByDesc("createTime"));
 * </pre>
 *
 * <p>字段名一律使用 Java 字段名，自动映射为数据库列名；
 * 找不到映射时立即抛出异常（fail-fast，杜绝 SQL 注入）。</p>
 *
 * @param <T> 实体类型
 * @author sombreknight
 */
public class QueryHelper<T extends BaseEntity> {

    private static final Logger log = LoggerFactory.getLogger(QueryHelper.class);

    private static final String KEY_WORD_WHERE = " where ";
    private static final String KEY_WORD_AND = " and ";
    private static final String KEY_WORD_ORDER_BY = " order by ";
    private static final String KEY_WORD_GROUP_BY = " group by ";
    private static final String KEY_WORD_LIMIT = " limit ";
    private static final String KEY_WORD_ASC = " asc ";
    private static final String KEY_WORD_DESC = " desc ";
    private static final String KEY_WORD_IN = " in ";
    private static final String KEY_WORD_NOT_IN = " not in ";
    private static final String KEY_WORD_GTE = " >= ";
    private static final String KEY_WORD_GT = " > ";
    private static final String KEY_WORD_LTE = " <= ";
    private static final String KEY_WORD_LT = " < ";
    private static final String KEY_WORD_LIKE = " like ";
    private static final String KEY_WORD_EQUAL = " = ";
    private static final String KEY_WORD_FORCE_INDEX = " force index ";
    private static final String KEY_WORD_FOR_UPDATE = " for update ";
    private static final String SEPARATOR_COMMA = ", ";
    private static final String SEPARATOR_LEFT = "(";
    private static final String SEPARATOR_RIGHT = ")";
    private static final String SEPARATOR_COLON = ":";

    private final Class<T> tableClass;
    private final ColumnMapper<T> columnMapper;

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
        this(tableClass, false);
    }

    public QueryHelper(Class<T> tableClass, boolean logShowSql) {
        this.tableClass = tableClass;
        this.columnMapper = Mapper.getInstance().getColumnMapper(tableClass);
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
     * 统计列：count(*) 或 count(字段)
     */
    public QueryHelper<T> countField(String... fields) {
        String expression = "*";
        if (fields != null && fields.length > 0 && fields[0] != null && !fields[0].trim().isEmpty()) {
            expression = getDbFieldName(fields[0].trim());
        }
        selectFieldList.add("count(" + expression + ")");
        return this;
    }

    // ==================== where 条件 ====================

    public QueryHelper<T> whereEqual(String field, Object value) {
        String column = getDbFieldName(field);
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_EQUAL).append(SEPARATOR_COLON).append(key);
        sqlParam.add(key, convertEnum(value));
        return this;
    }

    public <R> QueryHelper<T> whereIn(String field, List<R> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        if (values.size() == 1) {
            return whereEqual(field, values.get(0));
        }
        String column = getDbFieldName(field);
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_IN)
                .append(SEPARATOR_LEFT).append(SEPARATOR_COLON).append(key).append(SEPARATOR_RIGHT);
        sqlParam.add(key, values);
        return this;
    }

    public <R> QueryHelper<T> whereNotIn(String field, List<R> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        String column = getDbFieldName(field);
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_NOT_IN)
                .append(SEPARATOR_LEFT).append(SEPARATOR_COLON).append(key).append(SEPARATOR_RIGHT);
        sqlParam.add(key, values);
        return this;
    }

    public QueryHelper<T> whereGt(String field, Object value) {
        return range(field, value, KEY_WORD_GT);
    }

    public QueryHelper<T> whereGte(String field, Object value) {
        return range(field, value, KEY_WORD_GTE);
    }

    public QueryHelper<T> whereLt(String field, Object value) {
        return range(field, value, KEY_WORD_LT);
    }

    public QueryHelper<T> whereLte(String field, Object value) {
        return range(field, value, KEY_WORD_LTE);
    }

    public QueryHelper<T> whereLike(String field, String keyWord) {
        String column = getDbFieldName(field);
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(KEY_WORD_LIKE).append(SEPARATOR_COLON).append(key);
        sqlParam.add(key, keyWord);
        return this;
    }

    private QueryHelper<T> range(String field, Object value, String operator) {
        String column = getDbFieldName(field);
        String key = newPlaceKey(column);
        whereBlock.append(KEY_WORD_AND).append(column).append(operator).append(SEPARATOR_COLON).append(key);
        sqlParam.add(key, convertEnum(value));
        return this;
    }

    // ==================== 分组 / 排序 / 分页 ====================

    public QueryHelper<T> groupBy(String... fields) {
        if (fields == null || fields.length == 0) {
            return this;
        }
        List<String> columns = new ArrayList<>(fields.length);
        for (String field : fields) {
            columns.add(getDbFieldName(field));
        }
        groupByBlock.append(KEY_WORD_GROUP_BY).append(String.join(SEPARATOR_COMMA, columns));
        return this;
    }

    public QueryHelper<T> orderByAsc(String field) {
        orderByFieldList.add(getDbFieldName(field) + KEY_WORD_ASC);
        return this;
    }

    public QueryHelper<T> orderByDesc(String field) {
        orderByFieldList.add(getDbFieldName(field) + KEY_WORD_DESC);
        return this;
    }

    /**
     * 指定分页（配合 findPageByPageNum 时 JdbcDAO 自动拼接 limit）
     */
    public QueryHelper<T> limit(Integer page, Integer pageSize) {
        this.page = page;
        this.pageSize = pageSize;
        limitBlock.append(KEY_WORD_LIMIT).append(page).append(SEPARATOR_COMMA).append(pageSize);
        return this;
    }

    public QueryHelper<T> limit(Integer limit) {
        limitBlock.append(KEY_WORD_LIMIT).append(limit);
        return this;
    }

    public QueryHelper<T> limitOne() {
        return limit(1);
    }

    /**
     * 强制走索引（MySQL）
     */
    public QueryHelper<T> forceIndex(String indexName) {
        this.forceIndexName = indexName;
        this.forIndexSwitch = true;
        return this;
    }

    /**
     * 悲观锁
     */
    public QueryHelper<T> forUpdate() {
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
            sb.append(KEY_WORD_FORCE_INDEX).append(SEPARATOR_LEFT)
                    .append(forceIndexName).append(SEPARATOR_RIGHT);
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
            sb.append(KEY_WORD_FOR_UPDATE);
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
