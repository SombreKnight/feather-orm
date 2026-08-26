package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.annotation.EnumValue;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.mapping.FieldMeta;
import io.github.sombreknight.feather.test.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link EnumTypeHandler}：三层匹配（CodeEnum / @EnumValue / name）+ 未知值 fail-fast。
 */
class EnumTypeHandlerTest {

    private final EnumTypeHandler handler = new EnumTypeHandler();

    /** 普通枚举：默认按 name() 存取 */
    enum PlainStatus {
        ACTIVE, DISABLED
    }

    /** @EnumValue 逃生舱：按指定方法返回值存取 */
    enum CustomStatus {
        ACTIVE("on"), DISABLED("off");
        private final String code;

        CustomStatus(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    static class EnumEntity {
        @EnumValue("code")
        private CustomStatus status;
    }

    static class PlainEntity {
        private PlainStatus status;
    }

    @Test
    void unknownValueFailsFast() {
        assertThrows(FeatherDaoException.class,
                () -> EnumTypeHandler.valueOf("NOPE", OrderStatus.class, null));
        assertThrows(FeatherDaoException.class,
                () -> EnumTypeHandler.valueOf("42", PlainStatus.class, null));
        assertThrows(FeatherDaoException.class,
                () -> EnumTypeHandler.valueOf("", OrderStatus.class, null));
    }

    @Test
    void codeEnumMatchesByValue() {
        assertEquals(OrderStatus.CREATED, EnumTypeHandler.valueOf("1", OrderStatus.class, null));
        assertEquals(OrderStatus.PAID, EnumTypeHandler.valueOf("2", OrderStatus.class, null));
        assertEquals(OrderStatus.CANCELLED, EnumTypeHandler.valueOf("9", OrderStatus.class, null));
    }

    @Test
    void plainEnumMatchesByName() {
        assertEquals(PlainStatus.ACTIVE, EnumTypeHandler.valueOf("ACTIVE", PlainStatus.class, null));
        assertEquals(PlainStatus.DISABLED, EnumTypeHandler.valueOf("DISABLED", PlainStatus.class, null));
    }

    @Test
    void enumValueAnnotationMatchesByMethod() throws Exception {
        FieldMeta meta = FieldMeta.of(EnumEntity.class.getDeclaredField("status"));
        assertEquals(CustomStatus.ACTIVE, EnumTypeHandler.valueOf("on", CustomStatus.class, meta));
        assertEquals(CustomStatus.DISABLED, EnumTypeHandler.valueOf("off", CustomStatus.class, meta));
        // 未知业务码同样 fail-fast
        assertThrows(FeatherDaoException.class,
                () -> EnumTypeHandler.valueOf("bogus", CustomStatus.class, meta));
    }

    @Test
    void toJdbcValuePrefersCodeEnum() {
        assertEquals(2, handler.toJdbcValue(OrderStatus.PAID, null));
    }

    @Test
    void toJdbcValueFallsBackToEnumValueAnnotation() throws Exception {
        FieldMeta meta = FieldMeta.of(EnumEntity.class.getDeclaredField("status"));
        assertEquals("on", handler.toJdbcValue(CustomStatus.ACTIVE, meta));
    }

    @Test
    void toJdbcValueFallsBackToName() throws Exception {
        FieldMeta meta = FieldMeta.of(PlainEntity.class.getDeclaredField("status"));
        assertEquals("ACTIVE", handler.toJdbcValue(PlainStatus.ACTIVE, meta));
    }

    @Test
    void nullValuesArePassthrough() {
        assertNull(handler.toJdbcValue(null, null));
    }
}
