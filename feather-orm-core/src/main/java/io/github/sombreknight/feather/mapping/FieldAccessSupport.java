package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.util.ReflectUtils;

import java.lang.reflect.Field;

/**
 * 无 Setter 字段的写入支持（供生成的 RowMapper 调用）
 *
 * @author sombreknight
 */
public final class FieldAccessSupport {

    private FieldAccessSupport() {
    }

    /**
     * 反射写入字段值
     *
     * @param entity    目标对象
     * @param fieldName 字段名
     * @param value     值
     */
    public static void setFieldValue(Object entity, String fieldName, Object value)
            throws java.sql.SQLException {
        try {
            Field field = ReflectUtils.findField(entity.getClass(), fieldName, true);
            if (field == null) {
                throw new IllegalStateException("未找到字段: " + fieldName);
            }
            ReflectUtils.setFieldValue(field, entity, value);
        } catch (Exception e) {
            throw new java.sql.SQLException("写入字段[" + fieldName + "]失败", e);
        }
    }
}
