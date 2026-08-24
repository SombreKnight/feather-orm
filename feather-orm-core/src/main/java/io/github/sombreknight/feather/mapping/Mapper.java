package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.SqlDialect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 实体映射注册表（单例）
 *
 * <p>ColumnMapper 缓存按（实体类, 方言）分组：多个不同引擎的数据源可安全共存，
 * 各自按自己的方言生成表名/列名引用，互不污染（多数据源支持的基础）。</p>
 *
 * <p>无方言参数的调用使用全局默认方言——单数据源场景行为与旧版一致。</p>
 *
 * @author sombreknight
 */
public final class Mapper {

    private static final Mapper INSTANCE = new Mapper();

    private final Map<Class<?>, ConcurrentMap<SqlDialect, ColumnMapper<?>>> clazzMapperCache = new ConcurrentHashMap<>();
    private volatile SqlDialect dialect = DialectRegistry.defaultDialect();

    private Mapper() {
    }

    public static Mapper getInstance() {
        return INSTANCE;
    }

    /**
     * 当前默认数据库方言（供无方言参数的调用使用；单数据源场景由 starter 启动探测后替换）
     */
    public SqlDialect getDialect() {
        return dialect;
    }

    /**
     * 设置默认方言（仅影响无方言参数的调用；各数据源已按各自方言缓存的 ColumnMapper 不受影响）
     */
    public void setDialect(SqlDialect dialect) {
        if (dialect == null) {
            return;
        }
        this.dialect = dialect;
    }

    /**
     * 按实体类 + 方言获取 ColumnMapper（多数据源主路径：每个 JdbcDAO 传入自己的方言）
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseEntity<?>> ColumnMapper<T> getColumnMapper(Class<T> clazz, SqlDialect dialect) {
        final SqlDialect d = dialect == null ? this.dialect : dialect;
        return (ColumnMapper<T>) clazzMapperCache.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(d, dd -> new ColumnMapper<>((Class<T>) clazz, dd));
    }

    /**
     * 按实体类 + 全局默认方言获取 ColumnMapper（兼容旧调用，单数据源场景）
     */
    public <T extends BaseEntity<?>> ColumnMapper<T> getColumnMapper(Class<T> clazz) {
        return getColumnMapper(clazz, this.dialect);
    }
}
