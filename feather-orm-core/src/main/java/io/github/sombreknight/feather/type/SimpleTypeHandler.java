package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.mapping.FieldMeta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 简单类型处理器：基础类型、包装类型、String、BigDecimal、byte[]
 *
 * @author sombreknight
 */
public class SimpleTypeHandler implements TypeHandler {

    private static final Set<Class<?>> SUPPORTED = new HashSet<>(Arrays.asList(
            String.class,
            Integer.class, int.class,
            Long.class, long.class,
            Short.class, short.class,
            Byte.class, byte.class,
            Double.class, double.class,
            Float.class, float.class,
            Boolean.class, boolean.class,
            Character.class, char.class,
            java.math.BigDecimal.class,
            byte[].class
    ));

    @Override
    public boolean supports(Class<?> javaType, FieldMeta meta) {
        return SUPPORTED.contains(javaType);
    }

    @Override
    public Object toJdbcValue(Object value, FieldMeta meta) {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object fromResultSet(ResultSet rs, String column, FieldMeta meta) throws SQLException {
        Class<?> type = meta.getJavaType();
        if (type == String.class) {
            return rs.getString(column);
        }
        if (type == Integer.class || type == int.class) {
            int v = rs.getInt(column);
            return rs.wasNull() ? null : v;
        }
        if (type == Long.class || type == long.class) {
            long v = rs.getLong(column);
            return rs.wasNull() ? null : v;
        }
        if (type == Short.class || type == short.class) {
            short v = rs.getShort(column);
            return rs.wasNull() ? null : v;
        }
        if (type == Byte.class || type == byte.class) {
            byte v = rs.getByte(column);
            return rs.wasNull() ? null : v;
        }
        if (type == Double.class || type == double.class) {
            double v = rs.getDouble(column);
            return rs.wasNull() ? null : v;
        }
        if (type == Float.class || type == float.class) {
            float v = rs.getFloat(column);
            return rs.wasNull() ? null : v;
        }
        if (type == Boolean.class || type == boolean.class) {
            boolean v = rs.getBoolean(column);
            return rs.wasNull() ? null : v;
        }
        if (type == Character.class || type == char.class) {
            String s = rs.getString(column);
            return (s == null || s.isEmpty()) ? null : s.charAt(0);
        }
        if (type == java.math.BigDecimal.class) {
            return rs.getBigDecimal(column);
        }
        if (type == byte[].class) {
            return rs.getBytes(column);
        }
        return null;
    }

    /**
     * 供注册表使用的内置列表
     */
    public static List<TypeHandler> builtins() {
        List<TypeHandler> list = new ArrayList<>();
        list.add(new SimpleTypeHandler());
        list.add(new TemporalTypeHandler());
        list.add(new FeatherDateTypeHandler());
        list.add(new EnumTypeHandler());
        return list;
    }
}
