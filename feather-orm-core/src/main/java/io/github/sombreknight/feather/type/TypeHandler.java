package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.mapping.FieldMeta;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 类型处理器：负责 Java 类型与 JDBC 类型之间的双向转换
 *
 * <p>解析顺序（约定优于配置）：</p>
 * <ol>
 *     <li>用户注册的自定义 {@link TypeHandler}</li>
 *     <li>内置处理器：简单类型 → 时间类型 → FeatherDate → 枚举</li>
 *     <li>兜底：JSON 处理器（复杂对象 / 集合自动 JSON 序列化）</li>
 * </ol>
 *
 * @author sombreknight
 */
public interface TypeHandler {

    /**
     * 是否接管该字段类型
     *
     * @param javaType 字段类型
     * @param meta     字段元数据
     */
    boolean supports(Class<?> javaType, FieldMeta meta);

    /**
     * 写库方向：Java 值 → JDBC 参数值
     *
     * @param value Java 值（可能为 null）
     * @param meta  字段元数据
     * @return JDBC 参数值；返回 null 表示该字段不参与写库
     */
    Object toJdbcValue(Object value, FieldMeta meta);

    /**
     * 读库方向：ResultSet → Java 值
     *
     * @param rs     结果集
     * @param column 列名
     * @param meta   字段元数据
     * @return Java 值
     */
    Object fromResultSet(ResultSet rs, String column, FieldMeta meta) throws SQLException;
}
