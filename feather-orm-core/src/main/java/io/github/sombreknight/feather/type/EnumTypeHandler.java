package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.annotation.EnumValue;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.FieldMeta;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 枚举类型处理器（三层约定）
 *
 * <ol>
 *     <li>默认：按枚举 {@code name()} 存取</li>
 *     <li>实现了 {@link CodeEnum}：按 {@code getValue()} 存取</li>
 *     <li>字段标注 {@link EnumValue}：按指定方法返回值存取（逃生舱）</li>
 * </ol>
 *
 * @author sombreknight
 */
public class EnumTypeHandler implements TypeHandler {

    @Override
    public boolean supports(Class<?> javaType, FieldMeta meta) {
        return javaType.isEnum();
    }

    @Override
    public Object toJdbcValue(Object value, FieldMeta meta) {
        if (value == null) {
            return null;
        }
        if (value instanceof CodeEnum) {
            return ((CodeEnum<?>) value).getValue();
        }
        // meta 可能为 null（防御）：跳过 @EnumValue 分支，回退 name()
        EnumValue enumValue = meta == null ? null : meta.getEnumValueAnnotation();
        if (enumValue != null && !enumValue.value().trim().isEmpty()) {
            try {
                Method method = value.getClass().getMethod(enumValue.value().trim());
                return method.invoke(value);
            } catch (Exception e) {
                throw new FeatherDaoException("枚举[" + value.getClass().getName() + "]调用方法["
                        + enumValue.value() + "]失败", e);
            }
        }
        return ((Enum<?>) value).name();
    }

    @Override
    public Object fromResultSet(ResultSet rs, String column, FieldMeta meta) throws SQLException {
        Object columnValue = rs.getObject(column);
        if (columnValue == null) {
            return null;
        }
        return valueOf(String.valueOf(columnValue), meta.getJavaType(), meta);
    }

    /**
     * 按存储值查找枚举常量
     *
     * @param value    存储值（字符串化后的数据库值）
     * @param enumType 枚举类型
     * @param meta     字段元数据（可能为 null，用于 findField 场景）
     */
    @SuppressWarnings("unchecked")
    public static Object valueOf(String value, Class<?> enumType, FieldMeta meta) {
        Object[] constants = enumType.getEnumConstants();

        // 1. CodeEnum 按 getValue() 匹配
        if (CodeEnum.class.isAssignableFrom(enumType)) {
            for (Object constant : constants) {
                Object code = ((CodeEnum<?>) constant).getValue();
                if (code != null && String.valueOf(code).equals(value)) {
                    return constant;
                }
            }
        }

        // 2. @EnumValue 按指定方法返回值匹配
        EnumValue enumValue = meta == null ? null : meta.getEnumValueAnnotation();
        if (enumValue != null && !enumValue.value().trim().isEmpty()) {
            try {
                Method method = enumType.getMethod(enumValue.value().trim());
                for (Object constant : constants) {
                    Object code = method.invoke(constant);
                    if (code != null && String.valueOf(code).equals(value)) {
                        return constant;
                    }
                }
            } catch (Exception e) {
                throw new FeatherDaoException("枚举[" + enumType.getName() + "]调用方法["
                        + enumValue.value() + "]失败", e);
            }
        }

        // 3. 默认按 name() 匹配
        for (Object constant : constants) {
            if (((Enum<?>) constant).name().equals(value)) {
                return constant;
            }
        }

        throw new FeatherDaoException("无法将值[" + value + "]转换为枚举[" + enumType.getName() + "]");
    }
}
