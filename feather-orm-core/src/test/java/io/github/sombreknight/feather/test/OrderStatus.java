package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.type.CodeEnum;

/**
 * 业务码枚举：实现 CodeEnum，按 getValue() 存储
 *
 * @author sombreknight
 */
public enum OrderStatus implements CodeEnum<Integer> {

    CREATED(1),
    PAID(2),
    CANCELLED(9);

    private final Integer value;

    OrderStatus(Integer value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}
