package io.github.sombreknight.feather.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 枚举自定义存储值（逃生舱）
 *
 * <p>普通枚举默认按 {@code name()} 存储；实现 {@link io.github.sombreknight.feather.type.CodeEnum} 接口的枚举按
 * {@code getValue()} 存储。以上两种情况都无法满足时（例如无法修改源码的第三方枚举），
 * 可在字段上通过本注解指定取值方法的名称：</p>
 *
 * <pre>
 * &#064;EnumValue("getCode")
 * private ThirdPartyStatus status;
 * </pre>
 *
 * <p>注意：方法名是字符串，方法改名后编译期不会报错，因此仅在万不得已时使用。</p>
 *
 * @author sombreknight
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnumValue {

    /**
     * 枚举上用于获取存储值的方法名
     */
    String value();
}
