package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.SqlDialect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体映射注册表（单例）
 *
 * <p>持有全局 {@link SqlDialect}：ColumnMapper 构建时按当前方言生成引用后的表名/列名并缓存；
 * 方言变化时（如应用启动时探测完成）通过 {@link #setDialect} 重建全部缓存。</p>
 *
 * @author sombreknight
 */
public final class Mapper {

    private static final Mapper INSTANCE = new Mapper();

    private final Map<Class<?>, ColumnMapper<?>> clazzMapperCache = new ConcurrentHashMap<>();
    private volatile SqlDialect dialect = DialectRegistry.defaultDialect();

    private Mapper() {
    }

    public static Mapper getInstance() {
        return INSTANCE;
    }

    /**
     * 当前数据库方言（默认 DefaultDialect；stater 启动探测后自动替换）
     */
    public SqlDialect getDialect() {
        return dialect;
    }

    /**
     * 设置全局方言并重建映射缓存（方言影响标识符引用，已缓存的 ColumnMapper 必须失效）
     */
    public synchronized void setDialect(SqlDialect dialect) {
        if (dialect == null) {
            return;
        }
        this.dialect = dialect;
        clazzMapperCache.clear();
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseEntity<?>> ColumnMapper<T> getColumnMapper(Class<T> clazz) {
        return (ColumnMapper<T>) clazzMapperCache.computeIfAbsent(clazz,
                c -> new ColumnMapper<>((Class<T>) c, dialect));
    }
}
