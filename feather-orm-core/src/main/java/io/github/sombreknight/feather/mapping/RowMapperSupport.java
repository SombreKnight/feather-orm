package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.dialect.SqlDialect;
import io.github.sombreknight.feather.type.TypeHandler;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import io.github.sombreknight.feather.util.ReflectUtils;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RowMapper 支持类：字段处理器解析与 RowMapper 缓存
 *
 * <p>实体（DO）处理器依赖方言（ColumnMapper 按方言生成引用），缓存按（实体类, 方言）分组；
 * DTO 为纯约定映射不依赖方言，缓存仅按类分组。</p>
 *
 * @author sombreknight
 */
public class RowMapperSupport {

    private final TypeHandlerRegistry registry;
    private final RowMapperFactory factory;

    private final ConcurrentMap<Class<?>, ConcurrentMap<SqlDialect, FieldHandler[]>> doHandlerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, FieldHandler[]> dtoHandlerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, ConcurrentMap<SqlDialect, RowMapper<?>>> doMapperCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, RowMapper<?>> dtoMapperCache = new ConcurrentHashMap<>();

    public RowMapperSupport(TypeHandlerRegistry registry, RowMapperFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    /**
     * 获取实体 RowMapper（按方言缓存，多数据源互不污染）
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseEntity<?>> RowMapper<T> getRowMapper(Class<T> clazz, SqlDialect dialect) {
        return (RowMapper<T>) doMapperCache.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(dialect, d -> {
                    FieldHandler[] handlers = resolveHandlers((Class<T>) clazz, d);
                    return factory.createRowMapper((Class<T>) clazz, handlers, false);
                });
    }

    /**
     * 获取 DTO RowMapper（查询结果列可能不完整，列不存在时跳过）
     */
    @SuppressWarnings("unchecked")
    public <T> RowMapper<T> getDtoRowMapper(Class<T> clazz) {
        return (RowMapper<T>) dtoMapperCache.computeIfAbsent(clazz, c -> {
            FieldHandler[] handlers = resolveDtoHandlers((Class<T>) c);
            return factory.createRowMapper((Class<T>) c, handlers, true);
        });
    }

    /**
     * 解析实体字段处理器（供写库方向复用，按方言缓存）
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseEntity<?>> FieldHandler[] resolveHandlers(Class<T> clazz, SqlDialect dialect) {
        return doHandlerCache.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(dialect, d -> {
                    ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz, d);
                    List<FieldMeta> metas = mapper.getFieldMetas();
                    FieldHandler[] handlers = new FieldHandler[metas.size()];
                    for (int i = 0; i < metas.size(); i++) {
                        handlers[i] = resolve(metas.get(i), (Class<T>) clazz);
                    }
                    return handlers;
                });
    }

    /**
     * 解析 DTO 字段处理器（纯约定映射，缓存）
     */
    public <T> FieldHandler[] resolveDtoHandlers(Class<T> clazz) {
        return dtoHandlerCache.computeIfAbsent(clazz, c -> {
            Field[] fields = ReflectUtils.findFields(clazz, true);
            List<FieldHandler> handlers = new ArrayList<>();
            for (Field field : fields) {
                if (!ReflectUtils.isMappable(field) || Modifier.isVolatile(field.getModifiers())) {
                    continue;
                }
                handlers.add(resolve(FieldMeta.of(field), c));
            }
            return handlers.toArray(new FieldHandler[0]);
        });
    }

    private FieldHandler resolve(FieldMeta meta, Class<?> clazz) {
        TypeHandler handler = registry.resolve(meta.getJavaType(), meta);
        return new FieldHandler(handler, meta, meta.getColumn(), clazz);
    }
}
