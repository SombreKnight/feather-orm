package io.github.sombreknight.feather.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 列映射注解（可选）
 *
 * <p>默认采用驼峰转下划线约定：Java 字段 {@code userName} 自动映射数据库列 {@code user_name}。
 * 当字段名与列名不符合约定，或列名为数据库保留字时，使用本注解显式指定列名。</p>
 *
 * <pre>
 * &#064;Column("phone_no")
 * private String phone;
 * </pre>
 *
 * @author sombreknight
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Column {

    /**
     * 数据库字段名；为空时走驼峰转下划线约定
     */
    String value() default "";
}
