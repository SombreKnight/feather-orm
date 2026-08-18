package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.mapping.FieldMeta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * 时间类型处理器：java.util.Date、java.sql.*、java.time.*
 *
 * @author sombreknight
 */
public class TemporalTypeHandler implements TypeHandler {

    @Override
    public boolean supports(Class<?> javaType, FieldMeta meta) {
        if (Date.class.isAssignableFrom(javaType)) {
            return true;
        }
        return javaType == LocalDate.class
                || javaType == LocalDateTime.class
                || javaType == LocalTime.class
                || javaType == Instant.class
                || javaType == OffsetDateTime.class;
    }

    @Override
    public Object toJdbcValue(Object value, FieldMeta meta) {
        // Spring 的 StatementCreatorUtils 会正确处理 Date/Timestamp/LocalDateTime 等
        return value;
    }

    @Override
    public Object fromResultSet(ResultSet rs, String column, FieldMeta meta) throws SQLException {
        Class<?> type = meta.getJavaType();
        Timestamp ts = rs.getTimestamp(column);
        if (ts == null) {
            return null;
        }
        if (type == Date.class || type == java.sql.Timestamp.class) {
            return type == java.sql.Timestamp.class ? ts : new Date(ts.getTime());
        }
        if (type == java.sql.Date.class) {
            return new java.sql.Date(ts.getTime());
        }
        if (type == java.sql.Time.class) {
            return new java.sql.Time(ts.getTime());
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        if (type == LocalDateTime.class) {
            return ldt;
        }
        if (type == LocalDate.class) {
            return ldt.toLocalDate();
        }
        if (type == LocalTime.class) {
            return ldt.toLocalTime();
        }
        if (type == Instant.class) {
            return ts.toInstant();
        }
        if (type == OffsetDateTime.class) {
            return ldt.atOffset(ZoneOffset.systemDefault().getRules().getOffset(ldt));
        }
        return null;
    }
}
