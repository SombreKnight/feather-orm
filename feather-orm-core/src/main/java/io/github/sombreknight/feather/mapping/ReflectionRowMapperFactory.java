package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.util.ReflectUtils;
import org.springframework.jdbc.core.RowMapper;

import java.sql.SQLException;

/**
 * 纯反射 RowMapper 工厂（兜底实现）
 *
 * <p>当运行环境禁用字节码生成（安全策略、GraalVM 等）时使用。</p>
 *
 * @author sombreknight
 */
public class ReflectionRowMapperFactory implements RowMapperFactory {

    @Override
    public <T> RowMapper<T> createRowMapper(Class<T> clazz, FieldHandler[] handlers, boolean dto) {
        return (rs, rowNum) -> {
            T entity;
            try {
                entity = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SQLException("实例化实体[" + clazz.getName() + "]失败", e);
            }
            for (FieldHandler handler : handlers) {
                try {
                    Object value = handler.fromResultSet(rs);
                    ReflectUtils.setFieldValue(handler.getMeta().getField(), entity, value);
                } catch (SQLException e) {
                    if (!dto) {
                        throw e;
                    }
                    // DTO 模式下列不存在则跳过
                }
            }
            return entity;
        };
    }
}
