package io.github.sombreknight.feather.core;

/**
 * 主键 ID 生成器
 *
 * <p>默认实现为 {@link io.github.sombreknight.feather.core.SnowflakeIdGenerator}（雪花算法）。
 * 用户可通过注册自定义 {@link IdGenerator} Bean 覆盖。</p>
 *
 * @author sombreknight
 */
public interface IdGenerator {

    /**
     * 生成下一个唯一 id
     */
    long nextId();
}
