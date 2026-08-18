package io.github.sombreknight.feather.samples.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import io.github.sombreknight.feather.type.CodeEnum;

/**
 * 订单状态：实现 CodeEnum，按业务码存取
 *
 * <p>{@code @JsonValue} 让 REST 请求/响应的枚举统一使用业务码（1/2/9），与数据库一致。</p>
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

    @JsonValue
    @Override
    public Integer getValue() {
        return value;
    }
}
