package io.github.sombreknight.feather.mapping;

import org.springframework.jdbc.core.RowMapper;

/**
 * RowMapper 工厂 SPI
 *
 * <p>默认实现为 {@link JavassistRowMapperFactory}（运行时生成字节码，性能最优）；
 * 可通过配置切换到 {@link ReflectionRowMapperFactory}（纯反射兜底）。</p>
 *
 * @author sombreknight
 */
public interface RowMapperFactory {

    /**
     * 创建 RowMapper
     *
     * @param clazz    目标实体类型
     * @param handlers 字段处理器（已按字段顺序解析好）
     * @param dto       是否为 DTO 映射（true 时查询结果列可能不完整，列不存在则跳过；false 时列不存在抛异常）
     * @param <T>      实体泛型
     * @return RowMapper
     */
    <T> RowMapper<T> createRowMapper(Class<T> clazz, FieldHandler[] handlers, boolean dto);
}
