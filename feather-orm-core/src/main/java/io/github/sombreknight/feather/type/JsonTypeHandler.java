package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.mapping.FieldMeta;
import io.github.sombreknight.feather.util.JsonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 类型处理器（兜底）
 *
 * <p>凡未命中内置简单类型、时间类型、枚举的字段类型，一律按 JSON 字符串存储。
 * 反序列化时使用字段的泛型类型（{@code getGenericType()}），因此
 * {@code List<String>}、{@code Map<String,Object>}、嵌套泛型均无需额外注解。</p>
 *
 * <p>null 值语义：写库时跳过该列（insert 为 NULL / update 不触碰），绝不写空字符串。</p>
 *
 * @author sombreknight
 */
public class JsonTypeHandler implements TypeHandler {

    public static final JsonTypeHandler INSTANCE = new JsonTypeHandler();

    @Override
    public boolean supports(Class<?> javaType, FieldMeta meta) {
        return false; // 兜底处理器，永远不参与 supports 匹配
    }

    @Override
    public Object toJdbcValue(Object value, FieldMeta meta) {
        if (value == null) {
            return null;
        }
        return JsonUtils.toJson(value);
    }

    @Override
    public Object fromResultSet(ResultSet rs, String column, FieldMeta meta) throws SQLException {
        String json = rs.getString(column);
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JsonUtils.toObject(json, meta.getGenericType());
    }
}
