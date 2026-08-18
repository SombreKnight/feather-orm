package io.github.sombreknight.feather.core;

import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础 DAO：继承即获得全套 CRUD 能力
 *
 * <pre>
 * &#064;Repository
 * public class UserDAO extends BaseDAO&lt;UserEntity&gt; {
 * }
 *
 * // 使用
 * userDAO.saveEntity(userDO);
 * UserEntity user = userDAO.findById(1L);
 * List&lt;UserEntity&gt; list = userDAO.findList(userDAO.getQueryHelper().whereEqual("userName", "张三"));
 * </pre>
 *
 * @param <T> 实体类型
 * @author sombreknight
 */
public class BaseDAO<T extends BaseEntity> {

    @Autowired
    protected JdbcDAO jdbcDAO;

    // ==================== 新增 ====================

    public boolean saveEntity(T entity) {
        if (entity == null) {
            return false;
        }
        return 1 == jdbcDAO.save(entity);
    }

    public boolean saveEntityList(List<T> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return false;
        }
        return jdbcDAO.saveBatch(entityList).length == entityList.size();
    }

    /**
     * 新增或更新：有 id 且存在则更新，否则新增
     */
    public boolean saveOrUpdate(T entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getId() != null && jdbcDAO.findById(getEntityClass(), entity.getId()) != null) {
            return updateEntity(entity);
        }
        return saveEntity(entity);
    }

    // ==================== 删除 ====================

    public boolean deleteEntity(T entity) {
        if (entity == null || entity.getId() == null || entity.getId() <= 0) {
            return false;
        }
        return 1 == jdbcDAO.deleteEntity(getEntityClass(), entity);
    }

    public boolean deleteEntities(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        return jdbcDAO.deleteEntities(getEntityClass(), entities) == entities.size();
    }

    // ==================== 更新 ====================

    public boolean updateEntity(T entity) {
        if (entity == null || entity.getId() == null) {
            return false;
        }
        return 1 == jdbcDAO.update(entity);
    }

    public boolean updateEntityList(List<T> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return false;
        }
        int[] results = jdbcDAO.updateBatch(entityList);
        for (int result : results) {
            if (result == 0) {
                return false;
            }
        }
        return true;
    }

    // ==================== 按主键查询 ====================

    public T findById(Long id) {
        return jdbcDAO.findById(getEntityClass(), id);
    }

    public List<T> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return jdbcDAO.findByIds(getEntityClass(), ids);
    }

    public Map<Long, T> findMapByIds(List<Long> ids) {
        List<T> list = findByIds(ids);
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, T> map = new HashMap<>(list.size());
        for (T t : list) {
            map.put(t.getId(), t);
        }
        return map;
    }

    // ==================== 条件查询 ====================

    public QueryHelper<T> getQueryHelper() {
        return new QueryHelper<>(getEntityClass());
    }

    public T findOne(QueryHelper<T> queryHelper) {
        return jdbcDAO.findOne(getEntityClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam());
    }

    public List<T> findList(QueryHelper<T> queryHelper) {
        return jdbcDAO.findList(getEntityClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam());
    }

    public long count(QueryHelper<T> queryHelper) {
        return jdbcDAO.count(getEntityClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam());
    }

    public <F> F findField(Class<F> clazz, QueryHelper<T> queryHelper) {
        return jdbcDAO.findField(clazz, queryHelper.getSql(), queryHelper.getSqlParam());
    }

    public <F> List<F> findFieldList(Class<F> clazz, QueryHelper<T> queryHelper) {
        return jdbcDAO.findFieldList(clazz, queryHelper.getSql(), queryHelper.getSqlParam());
    }

    public PagingResult<T> findPageByPageNum(QueryHelper<T> queryHelper) {
        queryHelper.withPagination();
        return jdbcDAO.findPageByPageNum(getEntityClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam(),
                queryHelper.getPage(), queryHelper.getPageSize(), queryHelper.isWithTotal());
    }

    public <V> PagingResult<V> findDtoPageByPageNum(Class<V> dtoClass, QueryHelper<T> queryHelper) {
        queryHelper.withPagination();
        return jdbcDAO.findDtoPageByPageNum(dtoClass, queryHelper.getSql(), queryHelper.getSqlParam(),
                queryHelper.getPage(), queryHelper.getPageSize(), queryHelper.isWithTotal());
    }

    public <F> PagingResult<F> findFieldPageByPageNum(Class<F> clazz, QueryHelper<T> queryHelper) {
        queryHelper.withPagination();
        return jdbcDAO.findFieldPageByPageNum(clazz, queryHelper.getSql(), queryHelper.getSqlParam(),
                queryHelper.getPage(), queryHelper.getPageSize(), queryHelper.isWithTotal());
    }

    // ==================== 其他 ====================

    /**
     * 强制走主库（本线程本次操作）
     */
    public void forceMaster() {
        jdbcDAO.forceMaster();
    }

    /**
     * 获取实体类型（通过泛型解析）
     */
    @SuppressWarnings("unchecked")
    public Class<T> getEntityClass() {
        Type type = getClass().getGenericSuperclass();
        return (Class<T>) ((ParameterizedType) type).getActualTypeArguments()[0];
    }
}
