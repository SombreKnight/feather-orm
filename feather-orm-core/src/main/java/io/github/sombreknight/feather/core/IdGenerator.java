package io.github.sombreknight.feather.core;

/**
 * 主键 ID 生成器
 *
 * <p>内置实现：</p>
 * <ul>
 *     <li>{@link SnowflakeIdGenerator}（雪花算法，主键类型 {@code Long}）</li>
 *     <li>{@link UuidIdGenerator}（UUID 字符串，主键类型 {@code String}）</li>
 * </ul>
 *
 * <p>JdbcDAO 按实体主键的 Java 类型（见 {@code BaseEntity&lt;ID&gt;} 的泛型参数）自动匹配
 * 生成器；也可注册自定义实现 Bean，框架自动纳入匹配。</p>
 *
 * @param <ID> 生成的主键类型
 * @author sombreknight
 */
public interface IdGenerator<ID> {

    /**
     * 生成下一个唯一 id
     */
    ID nextId();

    /**
     * 生成器产生的主键 Java 类型（用于按实体主键类型自动匹配生成器）
     */
    Class<ID> idType();
}
