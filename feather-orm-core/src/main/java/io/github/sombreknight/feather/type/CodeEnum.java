package io.github.sombreknight.feather.type;

/**
 * 枚举存储值接口（第二层约定）
 *
 * <p>实现此接口的枚举，读写数据库时按 {@link #getValue()} 存取，而不是默认的 {@code name()}。</p>
 *
 * <pre>
 * public enum OrderStatus implements CodeEnum&lt;Integer&gt; {
 *     CREATED(1), PAID(2), CANCELLED(9);
 *     private final Integer value;
 *     OrderStatus(Integer value) { this.value = value; }
 *     public Integer getValue() { return value; }
 * }
 * </pre>
 *
 * @param <T> 存储值类型
 * @author sombreknight
 */
public interface CodeEnum<T> {

    /**
     * 枚举的存储值
     */
    T getValue();
}
