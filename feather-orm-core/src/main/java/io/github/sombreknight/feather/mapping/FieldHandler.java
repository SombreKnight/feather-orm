package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.type.TypeHandler;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字段处理器：字段元数据 + 类型处理器 + 列名的组合
 *
 * <p>在 RowMapper 生成阶段预解析并缓存：类型处理器、列名、以及字段读写访问器
 * （setter/getter 或字段的 MethodHandle，构建期一次性解析），运行时零反射、零查表。</p>
 *
 * @author sombreknight
 */
public class FieldHandler {

    private final TypeHandler handler;
    private final FieldMeta meta;
    private final String column;

    private final Field field;
    private final boolean primitive;
    private final MethodHandle getterHandle;   // (Object)Object，可为 null
    private final MethodHandle setterHandle;   // (Object,Object)void，可为 null
    private final boolean override;            // 已一次性 setAccessible(true)，可 Field 直读直写

    public FieldHandler(TypeHandler handler, FieldMeta meta, String column) {
        this(handler, meta, column, meta.getField().getDeclaringClass());
    }

    /**
     * @param clazz 实体类（用于查找继承/子类上的 public setter/getter）
     */
    public FieldHandler(TypeHandler handler, FieldMeta meta, String column, Class<?> clazz) {
        this.handler = handler;
        this.meta = meta;
        this.column = column;
        this.field = meta.getField();
        this.primitive = field.getType().isPrimitive();
        FieldAccessResolver.Accessor accessor = FieldAccessResolver.resolve(clazz, field);
        this.getterHandle = accessor.getter;
        this.setterHandle = accessor.setter;
        this.override = accessor.override;
    }

    public Object fromResultSet(ResultSet rs) throws SQLException {
        return handler.fromResultSet(rs, column, meta);
    }

    /**
     * 读取字段值（写库方向使用）：优先 getter MethodHandle，回退一次性 accessible 的 Field.get。
     */
    public Object getValue(Object target) {
        try {
            if (getterHandle != null) {
                return getterHandle.invoke(target);
            }
            return field.get(target);
        } catch (Throwable e) {
            throw new FeatherDaoException("读取字段[" + field.getName() + "]值失败", e);
        }
    }

    /**
     * 写入字段值（RowMapper 映射方向使用）：优先 setter MethodHandle，回退一次性 accessible 的 Field.set。
     *
     * @throws SQLException 原始类型字段写入 null、或字段访问异常（DTO 模式由调用方捕获跳过）
     */
    public void setValue(Object target, Object value) throws SQLException {
        try {
            if (setterHandle != null) {
                if (value == null && primitive) {
                    throw new IllegalArgumentException(
                            "不能将 null 写入原始类型字段[" + field.getName() + "]");
                }
                setterHandle.invoke(target, value);
            } else {
                field.set(target, value);
            }
        } catch (Throwable e) {
            throw new SQLException("写入字段[" + field.getName() + "]失败: " + e.getMessage(), e);
        }
    }

    public TypeHandler getHandler() {
        return handler;
    }

    public FieldMeta getMeta() {
        return meta;
    }

    public String getColumn() {
        return column;
    }
}
