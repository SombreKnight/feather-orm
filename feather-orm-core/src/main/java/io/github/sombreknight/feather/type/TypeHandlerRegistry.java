package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.mapping.FieldMeta;
import io.github.sombreknight.feather.exception.FeatherDaoException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 类型处理器注册表（单一事实源）
 *
 * <p>解析顺序：用户自定义处理器 &gt; 内置（简单类型 → 时间 → FeatherDate → 枚举）&gt; JSON 兜底。</p>
 *
 * @author sombreknight
 */
public class TypeHandlerRegistry {

    private final List<TypeHandler> userHandlers = new CopyOnWriteArrayList<>();
    private final List<TypeHandler> builtinHandlers;

    public TypeHandlerRegistry() {
        this.builtinHandlers = SimpleTypeHandler.builtins();
    }

    /**
     * 注册自定义处理器（优先级高于内置）
     */
    public void register(TypeHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("TypeHandler 不能为 null");
        }
        userHandlers.add(handler);
    }

    /**
     * 解析字段对应的处理器；无匹配时返回 JSON 兜底处理器
     */
    public TypeHandler resolve(Class<?> javaType, FieldMeta meta) {
        if (javaType == null) {
            throw new FeatherDaoException("字段类型不能为 null");
        }
        for (TypeHandler handler : userHandlers) {
            if (handler.supports(javaType, meta)) {
                return handler;
            }
        }
        for (TypeHandler handler : builtinHandlers) {
            if (handler.supports(javaType, meta)) {
                return handler;
            }
        }
        return JsonTypeHandler.INSTANCE;
    }
}
