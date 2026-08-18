package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.core.BaseEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体映射注册表（单例）
 *
 * @author sombreknight
 */
public final class Mapper {

    private static final Mapper INSTANCE = new Mapper();

    private final Map<Class<?>, ColumnMapper<?>> clazzMapperCache = new ConcurrentHashMap<>();

    private Mapper() {
    }

    public static Mapper getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseEntity> ColumnMapper<T> getColumnMapper(Class<T> clazz) {
        return (ColumnMapper<T>) clazzMapperCache.computeIfAbsent(clazz,
                c -> new ColumnMapper<>((Class<T>) c));
    }
}
