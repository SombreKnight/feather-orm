package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.exception.FeatherDaoException;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Constructor;
import java.sql.SQLException;

/**
 * 纯反射 RowMapper 工厂（兜底实现）
 *
 * <p>当运行环境禁用字节码生成（安全策略、GraalVM 等）时使用。</p>
 *
 * <p>性能优化（issue #3）：构造器在 RowMapper 构建期解析并缓存（消除逐行
 * {@code getDeclaredConstructor()} 查找）；字段写入走 {@link FieldHandler#setValue}
 * （构建期解析的 setter/字段 MethodHandle，消除逐行 {@code isAccessible/setAccessible} native 调用）。</p>
 *
 * @author sombreknight
 */
public class ReflectionRowMapperFactory implements RowMapperFactory {

    @Override
    public <T> RowMapper<T> createRowMapper(Class<T> clazz, FieldHandler[] handlers, boolean dto) {
        final Constructor<T> constructor = resolveConstructor(clazz);
        return (rs, rowNum) -> {
            T entity;
            try {
                entity = constructor.newInstance();
            } catch (Exception e) {
                throw new SQLException("实例化实体[" + clazz.getName() + "]失败", e);
            }
            for (FieldHandler handler : handlers) {
                try {
                    handler.setValue(entity, handler.fromResultSet(rs));
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

    /**
     * 构建期解析一次无参构造器并永久缓存（setAccessible(true) 覆盖私有构造器场景）
     */
    private static <T> Constructor<T> resolveConstructor(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new FeatherDaoException("实体[" + clazz.getName() + "]缺少无参构造器", e);
        }
    }
}
