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
 * public class UserDAO extends BaseDAO&lt;UserDO&gt; {
 * }
 *
 * // 使用
 * userDAO.saveDomain(userDO);
 * UserDO user = userDAO.findById(1L);
 * List&lt;UserDO&gt; list = userDAO.findList(userDAO.getQueryHelper().whereEqual("userName", "张三"));
 * </pre>
 *
 * @param <T> 实体类型
 * @author sombreknight
 */
public class BaseDAO<T extends BaseDO> {

    @Autowired
    protected JdbcDAO jdbcDAO;

    // ==================== 新增 ====================

    public boolean saveDomain(T domain) {
        if (domain == null) {
            return false;
        }
        return 1 == jdbcDAO.save(domain);
    }

    public boolean saveDomainList(List<T> domainList) {
        if (domainList == null || domainList.isEmpty()) {
            return false;
        }
        return jdbcDAO.saveBatch(domainList).length == domainList.size();
    }

    /**
     * 新增或更新：有 id 且存在则更新，否则新增
     */
    public boolean saveOrUpdate(T domain) {
        if (domain == null) {
            return false;
        }
        if (domain.getId() != null && jdbcDAO.findById(getDomainClass(), domain.getId()) != null) {
            return updateDomain(domain);
        }
        return saveDomain(domain);
    }

    // ==================== 删除 ====================

    public boolean deleteDomain(T domain) {
        if (domain == null || domain.getId() == null || domain.getId() <= 0) {
            return false;
        }
        return 1 == jdbcDAO.deleteDomain(getDomainClass(), domain);
    }

    public boolean deleteDomains(List<T> domains) {
        if (domains == null || domains.isEmpty()) {
            return false;
        }
        return jdbcDAO.deleteDomains(getDomainClass(), domains) == domains.size();
    }

    // ==================== 更新 ====================

    public boolean updateDomain(T domain) {
        if (domain == null || domain.getId() == null) {
            return false;
        }
        return 1 == jdbcDAO.update(domain);
    }

    public boolean updateDomainList(List<T> domainList) {
        if (domainList == null || domainList.isEmpty()) {
            return false;
        }
        int[] results = jdbcDAO.updateBatch(domainList);
        for (int result : results) {
            if (result == 0) {
                return false;
            }
        }
        return true;
    }

    // ==================== 按主键查询 ====================

    public T findById(Long id) {
        return jdbcDAO.findById(getDomainClass(), id);
    }

    public List<T> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return jdbcDAO.findByIds(getDomainClass(), ids);
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
        return new QueryHelper<>(getDomainClass());
    }

    public T findOne(QueryHelper<T> queryHelper) {
        return jdbcDAO.findOne(getDomainClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam());
    }

    public List<T> findList(QueryHelper<T> queryHelper) {
        return jdbcDAO.findList(getDomainClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam());
    }

    public long count(QueryHelper<T> queryHelper) {
        return jdbcDAO.count(getDomainClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam());
    }

    public <F> F findField(Class<F> clazz, QueryHelper<T> queryHelper) {
        return jdbcDAO.findField(clazz, queryHelper.getSql(), queryHelper.getSqlParam());
    }

    public <F> List<F> findFieldList(Class<F> clazz, QueryHelper<T> queryHelper) {
        return jdbcDAO.findFieldList(clazz, queryHelper.getSql(), queryHelper.getSqlParam());
    }

    public PagingResult<T> findPageByPageNum(QueryHelper<T> queryHelper) {
        queryHelper.withPagination();
        return jdbcDAO.findPageByPageNum(getDomainClass(), queryHelper.getWhereSql(), queryHelper.getSqlParam(),
                queryHelper.getPage(), queryHelper.getPageSize(), queryHelper.isWithTotal());
    }

    public <V> PagingResult<V> findVOPageByPageNum(Class<V> voClass, QueryHelper<T> queryHelper) {
        queryHelper.withPagination();
        return jdbcDAO.findVOPageByPageNum(voClass, queryHelper.getSql(), queryHelper.getSqlParam(),
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
    public Class<T> getDomainClass() {
        Type type = getClass().getGenericSuperclass();
        return (Class<T>) ((ParameterizedType) type).getActualTypeArguments()[0];
    }
}
