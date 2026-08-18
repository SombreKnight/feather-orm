package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.core.BaseDO;
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
 * @author sombreknight
 */
public class RowMapperSupport {

    private final TypeHandlerRegistry registry;
    private final RowMapperFactory factory;

    private final ConcurrentMap<Class<?>, FieldHandler[]> doHandlerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, FieldHandler[]> voHandlerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, RowMapper<?>> doMapperCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, RowMapper<?>> voMapperCache = new ConcurrentHashMap<>();

    public RowMapperSupport(TypeHandlerRegistry registry, RowMapperFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    /**
     * 获取实体（DO）RowMapper
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseDO> RowMapper<T> getRowMapper(Class<T> clazz) {
        return (RowMapper<T>) doMapperCache.computeIfAbsent(clazz, c -> {
            FieldHandler[] handlers = resolveHandlers((Class<T>) c);
            return factory.createRowMapper((Class<T>) c, handlers, false);
        });
    }

    /**
     * 获取只读 VO RowMapper（列不存在时跳过）
     */
    @SuppressWarnings("unchecked")
    public <T> RowMapper<T> getVORowMapper(Class<T> clazz) {
        return (RowMapper<T>) voMapperCache.computeIfAbsent(clazz, c -> {
            FieldHandler[] handlers = resolveVOHandlers((Class<T>) c);
            return factory.createRowMapper((Class<T>) c, handlers, true);
        });
    }

    /**
     * 解析实体字段处理器（供写库方向复用，缓存）
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseDO> FieldHandler[] resolveHandlers(Class<T> clazz) {
        return doHandlerCache.computeIfAbsent(clazz, c -> {
            ColumnMapper<T> mapper = Mapper.getInstance().getColumnMapper(clazz);
            List<FieldMeta> metas = mapper.getFieldMetas();
            FieldHandler[] handlers = new FieldHandler[metas.size()];
            for (int i = 0; i < metas.size(); i++) {
                handlers[i] = resolve(metas.get(i));
            }
            return handlers;
        });
    }

    /**
     * 解析 VO 字段处理器（纯约定映射，缓存）
     */
    public <T> FieldHandler[] resolveVOHandlers(Class<T> clazz) {
        return voHandlerCache.computeIfAbsent(clazz, c -> {
            Field[] fields = ReflectUtils.findFields(clazz, true);
            List<FieldHandler> handlers = new ArrayList<>();
            for (Field field : fields) {
                if (!ReflectUtils.isMappable(field) || Modifier.isVolatile(field.getModifiers())) {
                    continue;
                }
                handlers.add(resolve(FieldMeta.of(field)));
            }
            return handlers.toArray(new FieldHandler[0]);
        });
    }

    private FieldHandler resolve(FieldMeta meta) {
        TypeHandler handler = registry.resolve(meta.getJavaType(), meta);
        return new FieldHandler(handler, meta, meta.getColumn());
    }
}
