package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.dialect.SqlDialect;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.type.TypeHandler;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import io.github.sombreknight.feather.util.ReflectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>支持主工厂 + 降级工厂（issue #7）：主工厂（如 Javassist 字节码生成）生成失败时
 * 实例级一次性降级到 {@code fallbackFactory}（如纯反射），后续全部走降级工厂并打印 warn 日志。</p>
 *
 * @author sombreknight
 */
public class RowMapperSupport {

    private static final Logger log = LoggerFactory.getLogger(RowMapperSupport.class);

    private final TypeHandlerRegistry registry;
    private final RowMapperFactory factory;
    private final RowMapperFactory fallbackFactory;
    /** 主工厂生成失败后的实例级降级开关（环境性问题一次失败等于全部失败） */
    private volatile boolean degraded = false;

    private final ConcurrentMap<Class<?>, ConcurrentMap<SqlDialect, FieldHandler[]>> doHandlerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, FieldHandler[]> dtoHandlerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, ConcurrentMap<SqlDialect, RowMapper<?>>> doMapperCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, RowMapper<?>> dtoMapperCache = new ConcurrentHashMap<>();

    public RowMapperSupport(TypeHandlerRegistry registry, RowMapperFactory factory) {
        this(registry, factory, null);
    }

    public RowMapperSupport(TypeHandlerRegistry registry, RowMapperFactory factory, RowMapperFactory fallbackFactory) {
        this.registry = registry;
        this.factory = factory;
        this.fallbackFactory = fallbackFactory;
    }

    /**
     * 获取实体 RowMapper（按方言缓存，多数据源互不污染）
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseEntity<?>> RowMapper<T> getRowMapper(Class<T> clazz, SqlDialect dialect) {
        return (RowMapper<T>) doMapperCache.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(dialect, d -> {
                    FieldHandler[] handlers = resolveHandlers((Class<T>) clazz, d);
                    return createWithFallback((Class<T>) clazz, handlers, false, d.getName());
                });
    }

    /**
     * 获取 DTO RowMapper（查询结果列可能不完整，列不存在时跳过）
     */
    @SuppressWarnings("unchecked")
    public <T> RowMapper<T> getDtoRowMapper(Class<T> clazz) {
        return (RowMapper<T>) dtoMapperCache.computeIfAbsent(clazz, c -> {
            FieldHandler[] handlers = resolveDtoHandlers((Class<T>) c);
            return createWithFallback((Class<T>) c, handlers, true, "DTO");
        });
    }

    private <T> RowMapper<T> createWithFallback(Class<T> clazz, FieldHandler[] handlers, boolean dto, String label) {
        try {
            return effectiveFactory().createRowMapper(clazz, handlers, dto);
        } catch (FeatherDaoException e) {
            if (fallbackFactory == null) {
                throw e;
            }
            log.warn("RowMapper 字节码生成失败（{}），实例级降级为反射模式: {}", label, e.getMessage());
            degraded = true;
            return fallbackFactory.createRowMapper(clazz, handlers, dto);
        }
    }

    private RowMapperFactory effectiveFactory() {
        return (degraded && fallbackFactory != null) ? fallbackFactory : factory;
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
