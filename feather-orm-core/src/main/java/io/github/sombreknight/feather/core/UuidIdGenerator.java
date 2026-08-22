package io.github.sombreknight.feather.core;

import java.util.UUID;

/**
 * UUID 字符串主键生成器
 *
 * <p>生成标准 36 位 UUID 字符串（{@code 550e8400-e29b-41d4-a716-446655440000}），
 * 适用于 {@code BaseEntity&lt;String&gt;} 实体（如与历史系统 / 分布式 id 策略对齐的 String 主键表）。</p>
 *
 * @author sombreknight
 */
public class UuidIdGenerator implements IdGenerator<String> {

    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public Class<String> idType() {
        return String.class;
    }
}
