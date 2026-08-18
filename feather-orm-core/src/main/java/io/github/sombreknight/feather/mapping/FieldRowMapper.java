package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.type.EnumTypeHandler;
import io.github.sombreknight.feather.util.FeatherDate;
import io.github.sombreknight.feather.util.JsonUtils;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

/**
 * 单字段映射（findField / findFieldList 使用）
 *
 * @author sombreknight
 */
public class FieldRowMapper<T> implements RowMapper<T> {

    private final Class<T> requiredType;

    public FieldRowMapper(Class<T> requiredType) {
        this.requiredType = requiredType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        if (rsmd.getColumnCount() != 1) {
            throw new SQLException("findField 要求查询结果只有一列，实际为 " + rsmd.getColumnCount() + " 列");
        }

        Class<?> type = requiredType;
        if (type == String.class) {
            return (T) rs.getString(1);
        }
        if (type == Long.class || type == long.class) {
            long v = rs.getLong(1);
            return rs.wasNull() ? null : (T) Long.valueOf(v);
        }
        if (type == Integer.class || type == int.class) {
            int v = rs.getInt(1);
            return rs.wasNull() ? null : (T) Integer.valueOf(v);
        }
        if (type == Short.class || type == short.class) {
            short v = rs.getShort(1);
            return rs.wasNull() ? null : (T) Short.valueOf(v);
        }
        if (type == Byte.class || type == byte.class) {
            byte v = rs.getByte(1);
            return rs.wasNull() ? null : (T) Byte.valueOf(v);
        }
        if (type == Double.class || type == double.class) {
            double v = rs.getDouble(1);
            return rs.wasNull() ? null : (T) Double.valueOf(v);
        }
        if (type == Float.class || type == float.class) {
            float v = rs.getFloat(1);
            return rs.wasNull() ? null : (T) Float.valueOf(v);
        }
        if (type == Boolean.class || type == boolean.class) {
            boolean v = rs.getBoolean(1);
            return rs.wasNull() ? null : (T) Boolean.valueOf(v);
        }
        if (type == java.math.BigDecimal.class) {
            return (T) rs.getBigDecimal(1);
        }
        if (type == byte[].class) {
            return (T) rs.getBytes(1);
        }
        if (type == Date.class) {
            Timestamp ts = rs.getTimestamp(1);
            return ts == null ? null : (T) new Date(ts.getTime());
        }
        if (type == java.sql.Timestamp.class) {
            return (T) rs.getTimestamp(1);
        }
        if (type == LocalDateTime.class) {
            Timestamp ts = rs.getTimestamp(1);
            return ts == null ? null : (T) ts.toLocalDateTime();
        }
        if (type == LocalDate.class) {
            Timestamp ts = rs.getTimestamp(1);
            return ts == null ? null : (T) ts.toLocalDateTime().toLocalDate();
        }
        if (type == LocalTime.class) {
            Timestamp ts = rs.getTimestamp(1);
            return ts == null ? null : (T) ts.toLocalDateTime().toLocalTime();
        }
        if (type == FeatherDate.class) {
            Timestamp ts = rs.getTimestamp(1);
            return ts == null ? null : (T) new FeatherDate(ts.getTime());
        }
        if (type.isEnum()) {
            String s = rs.getString(1);
            if (s == null) {
                return null;
            }
            return (T) EnumTypeHandler.valueOf(s, type, null);
        }
        // JSON / 复杂对象
        String json = rs.getString(1);
        if (json == null || json.isEmpty()) {
            return null;
        }
        return (T) JsonUtils.toObject(json, type);
    }
}
