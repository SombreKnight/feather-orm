package io.github.sombreknight.feather.samples.domain;

import io.github.sombreknight.feather.type.CodeEnum;

/**
 * 订单状态：实现 CodeEnum，按业务码存取
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
