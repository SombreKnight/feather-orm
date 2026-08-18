package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.mapping.FieldMeta;
import io.github.sombreknight.feather.util.FeatherDate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * {@link FeatherDate} 类型处理器
 *
 * <p>零时间（ZERO_INST）写库时存为字符串 0000-00-00 00:00:00，需要数据库开启
 * zeroDateTimeBehavior 兼容策略（MySQL 连接参数）。</p>
 *
 * @author sombreknight
 */
public class FeatherDateTypeHandler implements TypeHandler {

    @Override
    public boolean supports(Class<?> javaType, FieldMeta meta) {
        return javaType == FeatherDate.class;
    }

    @Override
    public Object toJdbcValue(Object value, FieldMeta meta) {
        if (value == null) {
            return null;
        }
        FeatherDate fd = (FeatherDate) value;
        if (fd.isZeroTime()) {
            return "0000-00-00 00:00:00";
        }
        return new Timestamp(fd.getTime());
    }

    @Override
    public Object fromResultSet(ResultSet rs, String column, FieldMeta meta) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        if (ts == null) {
            return null;
        }
        return new FeatherDate(ts.getTime());
    }
}
