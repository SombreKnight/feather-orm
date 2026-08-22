package io.github.sombreknight.feather.core;

import io.github.sombreknight.feather.datasource.DataSourceHolder;
import io.github.sombreknight.feather.datasource.DataSourceKey;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.SqlDialect;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.ColumnMapper;
import io.github.sombreknight.feather.mapping.FieldHandler;
import io.github.sombreknight.feather.mapping.FieldRowMapper;
import io.github.sombreknight.feather.mapping.Mapper;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.util.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feather ORM 核心执行引擎（基于 NamedParameterJdbcTemplate）
 *
 * <p>能力：CRUD、批量、分页、单字段查询、只读 DTO 映射、一主多从路由（可选）、强制主库。</p>
 *
 * <p>读写分离规则：写操作与按主键查询走主库；普通查询走从库（未配置从库时全部走主库）。</p>
 *
 * @author sombreknight
 */
public class JdbcDAO {

    private static final Logger log = LoggerFactory.getLogger(JdbcDAO.class);

    private static final String PK_FIELD_NAME = "id";
    private static final int BATCH_SIZE = 100;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final List<IdGenerator<?>> idGenerators;
    private final Map<Class<?>, IdGenerator<?>> idGeneratorByType;
    private final RowMapperSupport rowMapperSupport;
    private final int slaveCount;
    private final SqlDialect dialect;

    private final ThreadLocal<Boolean> forceMaster = new ThreadLocal<>();

    /**
     * 兼容构造：多个主键生成器，方言使用默认（DefaultDialect）
     */
    public JdbcDAO(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                   List<IdGenerator<?>> idGenerators,
                   RowMapperSupport rowMapperSupport,
                   int slaveCount) {
        this(namedParameterJdbcTemplate, idGenerators, rowMapperSupport, slaveCount, DialectRegistry.defaultDialect());
    }

    /**
     * 兼容构造：单个主键生成器（实体主键类型须与该生成器 {@code idType()} 一致）
     */
    public JdbcDAO(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                   IdGenerator<?> idGenerator,
                   RowMapperSupport rowMapperSupport,
                   int slaveCount) {
        this(namedParameterJdbcTemplate, idGenerator, rowMapperSupport, slaveCount, DialectRegistry.defaultDialect());
    }

    /**
     * 兼容构造：单个主键生成器（实体主键类型须与该生成器 {@code idType()} 一致）
     */
    public JdbcDAO(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                   IdGenerator<?> idGenerator,
                   RowMapperSupport rowMapperSupport,
                   int slaveCount,
                   SqlDialect dialect) {
        this(namedParameterJdbcTemplate,
                idGenerator == null ? Collections.emptyList() : Collections.singletonList(idGenerator),
                rowMapperSupport, slaveCount, dialect);
    }

    /**
     * 主构造：多个主键生成器，按 {@link IdGenerator#idType()} 与实体主键类型自动匹配
     *
     * <p>如同时注册 {@code SnowflakeIdGenerator}（Long）与 {@code UuidIdGenerator}（String），
     * Long 主键实体用雪花、String 主键实体用 UUID；匹配不到时 fail-fast 报错。</p>
     */
    public JdbcDAO(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                   List<IdGenerator<?>> idGenerators,
                   RowMapperSupport rowMapperSupport,
                   int slaveCount,
                   SqlDialect dialect) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.rowMapperSupport = rowMapperSupport;
        this.slaveCount = slaveCount;
        this.dialect = dialect == null ? DialectRegistry.defaultDialect() : dialect;
        this.idGenerators = idGenerators == null ? Collections.emptyList() : idGenerators;
        this.idGeneratorByType = new HashMap<>();
        for (IdGenerator<?> generator : this.idGenerators) {
            if (generator != null && generator.idType() != null) {
                idGeneratorByType.put(generator.idType(), generator);
            }
        }
    }

    // ==================== 数据源路由 ====================

    /**
     * 强制后续操作走主库（仅对当前线程的本次操作生效）
     */
    public JdbcDAO forceMaster() {
        this.forceMaster.set(Boolean.TRUE);
        return this;
    }

    private void setDataSourceKey(boolean master) {
        if (slaveCount <= 0) {
            return;
        }
        if (master || forceMaster.get() != null) {
            DataSourceHolder.setDataSourceKey(DataSourceKey.MASTER);
        } else {
            DataSourceHolder.setDataSourceKey(DataSourceKey.SLAVE_PREFIX + RandomUtils.randomInt(1, slaveCount));
        }
    }

    private void clearDataSourceKey() {
        forceMaster.remove();
        DataSourceHolder.clearDataSource();
    }

    // ==================== 保存 ====================

    /**
     * 新增实体；id 为空时按实体主键类型自动生成（Long→雪花 / String→UUID），用户也可自行指定 id
     */
    public <T extends BaseEntity<?>> int save(T entity) {
        if (entity == null) {
            throw new FeatherDaoException("JdbcDAO.save: entity 不能为 null");
        }
        if (entity.getId() == null) {
            entity.setId(generateId(entity.getClass()));
        }
        InsertStatement statement = buildInsert(entity);
        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.update(statement.sql, statement.params);
        } catch (Exception e) {
            throw new FeatherDaoException("保存实体[" + entity.getClass().getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    /**
     * 批量新增；按"非空列集合"分组批量执行（组内共享同一 SQL）
     */
    public <T extends BaseEntity<?>> int[] saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new int[0];
        }
        for (T entity : entities) {
            if (entity.getId() == null) {
                entity.setId(generateId(entity.getClass()));
            }
        }
        // 按非空列集合分组，保证组内使用同一 SQL
        Map<List<String>, List<T>> groups = new LinkedHashMap<>();
        for (T entity : entities) {
            List<String> key = nonNullFieldNames(entity);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
        }
        List<Integer> results = new ArrayList<>(entities.size());
        setDataSourceKey(true);
        try {
            for (Map.Entry<List<String>, List<T>> entry : groups.entrySet()) {
                InsertStatement statement = buildInsert(entry.getValue().get(0));
                Map<String, Object>[] batchValues = new Map[entry.getValue().size()];
                for (int i = 0; i < entry.getValue().size(); i++) {
                    batchValues[i] = buildParams(entry.getValue().get(i), entry.getKey());
                }
                int[] ints = namedParameterJdbcTemplate.batchUpdate(statement.sql, batchValues);
                for (int anInt : ints) {
                    results.add(anInt);
                }
            }
        } catch (Exception e) {
            throw new FeatherDaoException("批量保存实体[" + entities.get(0).getClass().getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
        int[] array = new int[results.size()];
        for (int i = 0; i < results.size(); i++) {
            array[i] = results.get(i);
        }
        return array;
    }

    /**
     * 更新实体：仅更新非 null 字段；null 字段不触碰（避免误清数据）
     */
    public <T extends BaseEntity<?>> int update(T entity) {
        if (entity == null || entity.getId() == null) {
            throw new FeatherDaoException("JdbcDAO.update: entity 或 id 不能为 null");
        }
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) entity.getClass();
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);

        StringBuilder sets = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (FieldHandler handler : handlers) {
            Field field = handler.getMeta().getField();
            if (PK_FIELD_NAME.equals(field.getName())) {
                continue; // 主键不进 SET
            }
            Object value = handler.getValue(entity);
            Object jdbcValue = handler.getHandler().toJdbcValue(value, handler.getMeta());
            if (jdbcValue == null) {
                continue; // 非 null 字段才参与更新
            }
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append(handler.getMeta().getQuotedColumn()).append(" = :").append(field.getName());
            params.put(field.getName(), jdbcValue);
        }
        if (sets.length() == 0) {
            log.warn("更新实体[{}]时没有可更新的非空字段", clazz.getName());
            return 0;
        }
        params.put(PK_FIELD_NAME, entity.getId());
        String sql = " update " + mapper.getQuotedTableName() + " set " + sets
                + " where " + mapper.getQuotedIdColumn() + " = :" + PK_FIELD_NAME;

        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            throw new FeatherDaoException("更新实体[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    /**
     * 批量更新：单条 SQL + COALESCE 实现"仅更新非 null 字段"语义
     *
     * <p>SET 列 = COALESCE(:列, 原列)，参数为 null 时保留原值，非 null 时更新，
     * 与单条 {@link #update} 的语义完全一致，且一次 batchUpdate 完成。</p>
     */
    public <T extends BaseEntity<?>> int[] updateBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new int[0];
        }
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) entities.get(0).getClass();
        for (T entity : entities) {
            if (entity.getId() == null) {
                throw new FeatherDaoException("批量更新时实体 id 不能为 null");
            }
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);

        StringBuilder sets = new StringBuilder();
        List<FieldHandler> settableHandlers = new ArrayList<>();
        for (FieldHandler handler : handlers) {
            Field field = handler.getMeta().getField();
            if (PK_FIELD_NAME.equals(field.getName())) {
                continue;
            }
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append(handler.getMeta().getQuotedColumn())
                    .append(" = COALESCE(:").append(field.getName())
                    .append(", ").append(handler.getMeta().getQuotedColumn()).append(")");
            settableHandlers.add(handler);
        }
        if (settableHandlers.isEmpty()) {
            log.warn("批量更新实体[{}]时没有可更新的字段", clazz.getName());
            return new int[entities.size()];
        }
        String sql = " update " + mapper.getQuotedTableName() + " set " + sets
                + " where " + mapper.getQuotedIdColumn() + " = :" + PK_FIELD_NAME;

        Map<String, Object>[] batchValues = new Map[entities.size()];
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            Map<String, Object> params = new HashMap<>();
            for (FieldHandler handler : settableHandlers) {
                Field field = handler.getMeta().getField();
                Object value = handler.getValue(entity);
                Object jdbcValue = handler.getHandler().toJdbcValue(value, handler.getMeta());
                params.put(field.getName(), jdbcValue); // null 时 COALESCE 保留原值
            }
            params.put(PK_FIELD_NAME, entity.getId());
            batchValues[i] = params;
        }
        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.batchUpdate(sql, batchValues);
        } catch (Exception e) {
            throw new FeatherDaoException("批量更新实体[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    // ==================== 删除 ====================

    public <T extends BaseEntity<?>> int deleteEntity(Class<T> clazz, T entity) {
        if (entity == null || entity.getId() == null) {
            throw new FeatherDaoException("JdbcDAO.deleteEntity: entity 或 id 不能为 null");
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getDeleteSql() + " where " + mapper.getQuotedIdColumn() + " = :" + PK_FIELD_NAME;
        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.update(sql, Collections.singletonMap(PK_FIELD_NAME, entity.getId()));
        } catch (Exception e) {
            throw new FeatherDaoException("删除实体[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T extends BaseEntity<?>> int deleteEntities(Class<T> clazz, List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        List<Object> ids = new ArrayList<>(entities.size());
        for (T entity : entities) {
            ids.add(entity.getId());
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getDeleteSql() + " where " + mapper.getQuotedIdColumn() + " in (:" + PK_FIELD_NAME + ")";
        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.update(sql, Collections.singletonMap(PK_FIELD_NAME, ids));
        } catch (Exception e) {
            throw new FeatherDaoException("批量删除实体[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    // ==================== 按主键查询 ====================

    /**
     * 按主键查询；id 类型须与实体主键类型一致（BaseDAO 层提供类型安全包装）
     */
    public <T extends BaseEntity<?>> T findById(Class<T> clazz, Object id) {
        if (id == null) {
            return null;
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getFromSql() + " where " + mapper.getQuotedIdColumn() + " = :" + PK_FIELD_NAME;
        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.queryForObject(sql,
                    Collections.singletonMap(PK_FIELD_NAME, id),
                    rowMapperSupport.getRowMapper(clazz));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            throw new FeatherDaoException("按主键查询[" + clazz.getName() + "]结果多于一条，id=" + id, e);
        } catch (Exception e) {
            throw new FeatherDaoException("按主键查询[" + clazz.getName() + "]失败，id=" + id, e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T extends BaseEntity<?>> List<T> findByIds(Class<T> clazz, Collection<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        if (ids.size() > BATCH_SIZE) {
            log.warn("findByIds 数量超过 {}，将分批查询，size={}", BATCH_SIZE, ids.size());
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getFromSql() + " where " + mapper.getQuotedIdColumn() + " in (:" + PK_FIELD_NAME + ")";
        List<T> result = new ArrayList<>(ids.size());
        setDataSourceKey(true);
        try {
            for (List<?> partition : partition(new ArrayList<>(ids), BATCH_SIZE)) {
                result.addAll(namedParameterJdbcTemplate.query(sql,
                        Collections.singletonMap(PK_FIELD_NAME, partition),
                        rowMapperSupport.getRowMapper(clazz)));
            }
            return result;
        } catch (Exception e) {
            throw new FeatherDaoException("按主键批量查询[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    // ==================== 条件查询 ====================

    public <T extends BaseEntity<?>> T findOne(Class<T> clazz, String whereSql, SqlParam param) {
        checkParam(param);
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getFromSql() + whereSql;
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.queryForObject(sql, paramToMap(param),
                    rowMapperSupport.getRowMapper(clazz));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            throw new FeatherDaoException("findOne[" + clazz.getName() + "]结果多于一条", e);
        } catch (Exception e) {
            throw new FeatherDaoException("findOne[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T extends BaseEntity<?>> List<T> findList(Class<T> clazz, String whereSql, SqlParam param) {
        checkParam(param);
        if (whereSql == null || whereSql.trim().isEmpty()) {
            throw new FeatherDaoException("findList 需要 where 条件，请使用 QueryHelper 拼装");
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getFromSql() + whereSql;
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.query(sql, paramToMap(param),
                    rowMapperSupport.getRowMapper(clazz));
        } catch (Exception e) {
            throw new FeatherDaoException("findList[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T extends BaseEntity<?>> long count(Class<T> clazz, String whereSql, SqlParam param) {
        checkParam(param);
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        // 剥离 order by / for update：PostgreSQL 等对 count(*) 带 order by 直接报错
        String sql = mapper.getCountSql() + dialect.stripTailForCount(whereSql);
        setDataSourceKey(false);
        try {
            Long value = namedParameterJdbcTemplate.queryForObject(sql, paramToMap(param), Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            throw new FeatherDaoException("count[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    /**
     * 分页查询实体
     */
    public <T extends BaseEntity<?>> PagingResult<T> findPageByPageNum(Class<T> clazz, String whereSql, SqlParam param,
                                                                int page, int size, boolean withTotal) {
        checkParam(param);
        int skip = (page - 1) * size;
        long total = 0;
        if (withTotal) {
            total = count(clazz, whereSql, param);
            if (total == 0) {
                return new PagingResult<>(new PageInfo(total, page, size), Collections.emptyList());
            }
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getFromSql() + whereSql + orderByForPaging(whereSql) + dialect.limitClause(skip, size);
        setDataSourceKey(false);
        try {
            List<T> list = namedParameterJdbcTemplate.query(sql, paramToMap(param),
                    rowMapperSupport.getRowMapper(clazz));
            return new PagingResult<>(new PageInfo(total, page, size), list);
        } catch (Exception e) {
            throw new FeatherDaoException("分页查询[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    // ==================== 只读 DTO 查询 ====================

    /**
     * 查询单个 DTO（任意 POJO，查询结果列不存在自动跳过）
     */
    public <T> T findDto(Class<T> dtoClass, String sql, SqlParam param) {
        checkParam(param);
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.queryForObject(sql, paramToMap(param),
                    rowMapperSupport.getDtoRowMapper(dtoClass));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            throw new FeatherDaoException("findDto[" + dtoClass.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T> List<T> findDtoList(Class<T> dtoClass, String sql, SqlParam param) {
        checkParam(param);
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.query(sql, paramToMap(param),
                    rowMapperSupport.getDtoRowMapper(dtoClass));
        } catch (Exception e) {
            throw new FeatherDaoException("findDtoList[" + dtoClass.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T> PagingResult<T> findDtoPageByPageNum(Class<T> dtoClass, String sql, SqlParam param,
                                                   int page, int size, boolean withTotal) {
        checkParam(param);
        int skip = (page - 1) * size;
        long total = 0;
        if (withTotal) {
            total = findFieldWithoutException(Long.class, dialect.wrapCount(sql), param);
            if (total == 0) {
                return new PagingResult<>(new PageInfo(total, page, size), Collections.emptyList());
            }
        }
        String pageSql = sql + orderByForPaging(sql) + dialect.limitClause(skip, size);
        setDataSourceKey(false);
        try {
            List<T> list = namedParameterJdbcTemplate.query(pageSql, paramToMap(param),
                    rowMapperSupport.getDtoRowMapper(dtoClass));
            return new PagingResult<>(new PageInfo(total, page, size), list);
        } catch (Exception e) {
            throw new FeatherDaoException("DTO 分页查询[" + dtoClass.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    // ==================== 单字段查询 ====================

    public <T> T findField(Class<T> clazz, String sql, SqlParam param) {
        checkParam(param);
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.queryForObject(sql, paramToMap(param), new FieldRowMapper<>(clazz));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            throw new FeatherDaoException("findField[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T> List<T> findFieldList(Class<T> clazz, String sql, SqlParam param) {
        checkParam(param);
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.query(sql, paramToMap(param), new FieldRowMapper<>(clazz));
        } catch (Exception e) {
            throw new FeatherDaoException("findFieldList[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T> PagingResult<T> findFieldPageByPageNum(Class<T> clazz, String sql, SqlParam param,
                                                      int page, int size, boolean withTotal) {
        checkParam(param);
        int skip = (page - 1) * size;
        long total = 0;
        if (withTotal) {
            total = findFieldWithoutException(Long.class, dialect.wrapCount(sql), param);
            if (total == 0) {
                return new PagingResult<>(new PageInfo(total, page, size), Collections.emptyList());
            }
        }
        String pageSql = sql + orderByForPaging(sql) + dialect.limitClause(skip, size);
        setDataSourceKey(false);
        try {
            List<T> list = namedParameterJdbcTemplate.query(pageSql, paramToMap(param), new FieldRowMapper<>(clazz));
            return new PagingResult<>(new PageInfo(total, page, size), list);
        } catch (Exception e) {
            throw new FeatherDaoException("字段分页查询[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    private long findFieldWithoutException(Class<Long> clazz, String sql, SqlParam param) {
        Long value = findField(clazz, sql, param);
        return value == null ? 0L : value;
    }

    /**
     * OFFSET/FETCH 类方言（SQL Server / Oracle）分页要求必须有 ORDER BY；
     * 无排序时自动补 {@code order by (select 0)} 满足语法，不影响结果集内容。
     */
    private String orderByForPaging(String sql) {
        if (dialect.requiresOrderByForPaging() && !containsOrderBy(sql)) {
            return " order by (select 0) ";
        }
        return "";
    }

    private static boolean containsOrderBy(String sql) {
        return sql != null && sql.toLowerCase().contains("order by");
    }

    // ==================== 内部方法 ====================

    private static class InsertStatement {
        final String sql;
        final Map<String, Object> params;

        InsertStatement(String sql, Map<String, Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends BaseEntity<?>> InsertStatement buildInsert(T entity) {
        Class<T> clazz = (Class<T>) entity.getClass();
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (FieldHandler handler : handlers) {
            Field field = handler.getMeta().getField();
            Object value = handler.getValue(entity);
            Object jdbcValue = handler.getHandler().toJdbcValue(value, handler.getMeta());
            if (jdbcValue == null) {
                continue; // null 值跳过：insert 为 NULL（DB 默认值生效），绝不写空串
            }
            if (columns.length() > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(handler.getMeta().getQuotedColumn());
            placeholders.append(":").append(field.getName());
            params.put(field.getName(), jdbcValue);
        }
        if (columns.length() == 0) {
            throw new FeatherDaoException("实体[" + clazz.getName() + "]没有任何可插入的列");
        }
        String sql = " insert into " + mapper.getQuotedTableName() + " (" + columns + ") values (" + placeholders + ") ";
        return new InsertStatement(sql, params);
    }

    /**
     * 按指定列集合构建参数（批量插入组内实体使用，保证与组 SQL 列一致）
     */
    private <T extends BaseEntity<?>> Map<String, Object> buildParams(T entity, List<String> fieldNames) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) entity.getClass();
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);
        Map<String, Object> params = new HashMap<>();
        for (FieldHandler handler : handlers) {
            String fieldName = handler.getMeta().getField().getName();
            if (!fieldNames.contains(fieldName)) {
                continue;
            }
            Object value = handler.getValue(entity);
            // 必须经过 TypeHandler 转换（枚举→业务码、复杂对象→JSON、FeatherDate→Timestamp）
            Object jdbcValue = handler.getHandler().toJdbcValue(value, handler.getMeta());
            params.put(fieldName, jdbcValue);
        }
        return params;
    }

    /**
     * 实体的非空字段名列表（作为批量插入分组 key）
     */
    private <T extends BaseEntity<?>> List<String> nonNullFieldNames(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) entity.getClass();
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);
        List<String> names = new ArrayList<>();
        for (FieldHandler handler : handlers) {
            Object value = handler.getValue(entity);
            Object jdbcValue = handler.getHandler().toJdbcValue(value, handler.getMeta());
            if (jdbcValue != null) {
                names.add(handler.getMeta().getField().getName());
            }
        }
        return names;
    }

    private static List<List<?>> partition(List<?> list, int size) {
        List<List<?>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    /**
     * 按实体主键类型选择主键生成器并生成 id
     *
     * @param entityClass 实体类
     * @return 生成的 id（类型与实体主键类型一致）
     */
    @SuppressWarnings("unchecked")
    private <ID> ID generateId(Class<?> entityClass) {
        IdGenerator<?> generator = idGeneratorFor(entityClass);
        return (ID) generator.nextId();
    }

    /**
     * 按实体主键类型匹配生成器；匹配不到 fail-fast（提示已注册的生成器）
     */
    @SuppressWarnings("unchecked")
    private IdGenerator<?> idGeneratorFor(Class<?> entityClass) {
        ColumnMapper<BaseEntity<?>> mapper =
                (ColumnMapper<BaseEntity<?>>) Mapper.getInstance().getColumnMapper((Class<BaseEntity<?>>) entityClass);
        Class<?> pkType = mapper.getPkType();
        IdGenerator<?> generator = idGeneratorByType.get(pkType);
        if (generator == null) {
            throw new FeatherDaoException("实体[" + entityClass.getName() + "]主键类型[" + pkType.getName()
                    + "]没有匹配的 IdGenerator，已注册: " + idGeneratorByType.keySet()
                    + "（可注册自定义 IdGenerator Bean 或实现 IdGenerator.idType()）");
        }
        return generator;
    }

    /**
     * 校验参数：存在 null 值参数时立即失败（fail-fast）
     */
    private static void checkParam(SqlParam param) {
        if (param != null && !param.getNullParamList().isEmpty()) {
            throw new FeatherDaoException("SQL 参数存在 null 值，禁止执行: " + param.getNullParamList());
        }
    }

    private static Map<String, ?> paramToMap(SqlParam param) {
        return param == null ? null : param.toMap();
    }
}
