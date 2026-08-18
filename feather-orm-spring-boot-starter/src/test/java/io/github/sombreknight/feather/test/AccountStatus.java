package io.github.sombreknight.feather.test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.github.sombreknight.feather.type.CodeEnum;

/**
 * 业务码枚举（starter 测试用）
 *
 * @author sombreknight
 */
public enum AccountStatus implements CodeEnum<Integer> {

    NORMAL(0),
    FROZEN(1);

    private final Integer value;

    AccountStatus(Integer value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public Integer getValue() {
        return value;
    }
}
