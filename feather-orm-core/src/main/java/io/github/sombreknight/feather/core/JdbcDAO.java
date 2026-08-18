package io.github.sombreknight.feather.core;

import io.github.sombreknight.feather.datasource.DataSourceHolder;
import io.github.sombreknight.feather.datasource.DataSourceKey;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.ColumnMapper;
import io.github.sombreknight.feather.mapping.FieldHandler;
import io.github.sombreknight.feather.mapping.FieldRowMapper;
import io.github.sombreknight.feather.mapping.Mapper;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.util.RandomUtils;
import io.github.sombreknight.feather.util.ReflectUtils;
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
 * <p>能力：CRUD、批量、分页、单字段查询、只读 VO 映射、一主多从路由（可选）、强制主库。</p>
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
    private final IdGenerator idGenerator;
    private final RowMapperSupport rowMapperSupport;
    private final int slaveCount;

    private final ThreadLocal<Boolean> forceMaster = new ThreadLocal<>();

    public JdbcDAO(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                   IdGenerator idGenerator,
                   RowMapperSupport rowMapperSupport,
                   int slaveCount) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.idGenerator = idGenerator;
        this.rowMapperSupport = rowMapperSupport;
        this.slaveCount = slaveCount;
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
     * 新增实体；id 为空时自动生成（雪花），用户也可自行指定 id
     */
    public <T extends BaseDO> int save(T domain) {
        if (domain == null) {
            throw new FeatherDaoException("JdbcDAO.save: domain 不能为 null");
        }
        if (domain.getId() == null) {
            domain.setId(idGenerator.nextId());
        }
        InsertStatement statement = buildInsert(domain);
        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.update(statement.sql, statement.params);
        } catch (Exception e) {
            throw new FeatherDaoException("保存实体[" + domain.getClass().getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    /**
     * 批量新增；按"非空列集合"分组批量执行（组内共享同一 SQL）
     */
    public <T extends BaseDO> int[] saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new int[0];
        }
        for (T entity : entities) {
            if (entity.getId() == null) {
                entity.setId(idGenerator.nextId());
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
    public <T extends BaseDO> int update(T domain) {
        if (domain == null || domain.getId() == null) {
            throw new FeatherDaoException("JdbcDAO.update: domain 或 id 不能为 null");
        }
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) domain.getClass();
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);

        StringBuilder sets = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (FieldHandler handler : handlers) {
            Field field = handler.getMeta().getField();
            if (PK_FIELD_NAME.equals(field.getName())) {
                continue; // 主键不进 SET
            }
            Object value = ReflectUtils.getFieldValue(field, domain);
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
        params.put(PK_FIELD_NAME, domain.getId());
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
    public <T extends BaseDO> int[] updateBatch(List<T> entities) {
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
                Object value = ReflectUtils.getFieldValue(field, entity);
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

    public <T extends BaseDO> int deleteDomain(Class<T> clazz, T domain) {
        if (domain == null || domain.getId() == null) {
            throw new FeatherDaoException("JdbcDAO.deleteDomain: domain 或 id 不能为 null");
        }
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getDeleteSql() + " where " + mapper.getQuotedIdColumn() + " = :" + PK_FIELD_NAME;
        setDataSourceKey(true);
        try {
            return namedParameterJdbcTemplate.update(sql, Collections.singletonMap(PK_FIELD_NAME, domain.getId()));
        } catch (Exception e) {
            throw new FeatherDaoException("删除实体[" + clazz.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T extends BaseDO> int deleteDomains(Class<T> clazz, List<T> domains) {
        if (domains == null || domains.isEmpty()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>(domains.size());
        for (T domain : domains) {
            ids.add(domain.getId());
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

    public <T extends BaseDO> T findById(Class<T> clazz, Long id) {
        if (id == null || id <= 0) {
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

    public <T extends BaseDO> List<T> findByIds(Class<T> clazz, Collection<Long> ids) {
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
            for (List<Long> partition : partition(new ArrayList<>(ids), BATCH_SIZE)) {
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

    public <T extends BaseDO> T findOne(Class<T> clazz, String whereSql, SqlParam param) {
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

    public <T extends BaseDO> List<T> findList(Class<T> clazz, String whereSql, SqlParam param) {
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

    public <T extends BaseDO> long count(Class<T> clazz, String whereSql, SqlParam param) {
        checkParam(param);
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        String sql = mapper.getCountSql() + whereSql;
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
    public <T extends BaseDO> PagingResult<T> findPageByPageNum(Class<T> clazz, String whereSql, SqlParam param,
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
        String sql = mapper.getFromSql() + whereSql + " limit " + skip + ", " + size;
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

    // ==================== 只读 VO 查询 ====================

    /**
     * 查询单个 VO（任意 POJO，列不存在自动跳过）
     */
    public <T> T findVO(Class<T> voClass, String sql, SqlParam param) {
        checkParam(param);
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.queryForObject(sql, paramToMap(param),
                    rowMapperSupport.getVORowMapper(voClass));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            throw new FeatherDaoException("findVO[" + voClass.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T> List<T> findVOList(Class<T> voClass, String sql, SqlParam param) {
        checkParam(param);
        setDataSourceKey(false);
        try {
            return namedParameterJdbcTemplate.query(sql, paramToMap(param),
                    rowMapperSupport.getVORowMapper(voClass));
        } catch (Exception e) {
            throw new FeatherDaoException("findVOList[" + voClass.getName() + "]失败", e);
        } finally {
            clearDataSourceKey();
        }
    }

    public <T> PagingResult<T> findVOPageByPageNum(Class<T> voClass, String sql, SqlParam param,
                                                   int page, int size, boolean withTotal) {
        checkParam(param);
        int skip = (page - 1) * size;
        long total = 0;
        if (withTotal) {
            total = findFieldWithoutException(Long.class, " select count(*) from (" + sql + ") a ", param);
            if (total == 0) {
                return new PagingResult<>(new PageInfo(total, page, size), Collections.emptyList());
            }
        }
        String pageSql = sql + " limit " + skip + ", " + size;
        setDataSourceKey(false);
        try {
            List<T> list = namedParameterJdbcTemplate.query(pageSql, paramToMap(param),
                    rowMapperSupport.getVORowMapper(voClass));
            return new PagingResult<>(new PageInfo(total, page, size), list);
        } catch (Exception e) {
            throw new FeatherDaoException("VO 分页查询[" + voClass.getName() + "]失败", e);
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
            total = findFieldWithoutException(Long.class, " select count(*) from (" + sql + ") a ", param);
            if (total == 0) {
                return new PagingResult<>(new PageInfo(total, page, size), Collections.emptyList());
            }
        }
        String pageSql = sql + " limit " + skip + ", " + size;
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
    private <T extends BaseDO> InsertStatement buildInsert(T domain) {
        Class<T> clazz = (Class<T>) domain.getClass();
        ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (FieldHandler handler : handlers) {
            Field field = handler.getMeta().getField();
            Object value = ReflectUtils.getFieldValue(field, domain);
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
    private <T extends BaseDO> Map<String, Object> buildParams(T domain, List<String> fieldNames) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) domain.getClass();
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);
        Map<String, Object> params = new HashMap<>();
        for (FieldHandler handler : handlers) {
            String fieldName = handler.getMeta().getField().getName();
            if (!fieldNames.contains(fieldName)) {
                continue;
            }
            Object value = ReflectUtils.getFieldValue(handler.getMeta().getField(), domain);
            // 必须经过 TypeHandler 转换（枚举→业务码、复杂对象→JSON、FeatherDate→Timestamp）
            Object jdbcValue = handler.getHandler().toJdbcValue(value, handler.getMeta());
            params.put(fieldName, jdbcValue);
        }
        return params;
    }

    /**
     * 实体的非空字段名列表（作为批量插入分组 key）
     */
    private <T extends BaseDO> List<String> nonNullFieldNames(T domain) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) domain.getClass();
        FieldHandler[] handlers = rowMapperSupport.resolveHandlers(clazz);
        List<String> names = new ArrayList<>();
        for (FieldHandler handler : handlers) {
            Object value = ReflectUtils.getFieldValue(handler.getMeta().getField(), domain);
            Object jdbcValue = handler.getHandler().toJdbcValue(value, handler.getMeta());
            if (jdbcValue != null) {
                names.add(handler.getMeta().getField().getName());
            }
        }
        return names;
    }

    private static List<List<Long>> partition(List<Long> list, int size) {
        List<List<Long>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
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
