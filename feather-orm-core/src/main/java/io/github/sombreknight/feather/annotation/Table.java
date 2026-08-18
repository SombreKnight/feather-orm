package io.github.sombreknight.feather.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体表映射注解
 *
 * <p>用法：</p>
 * <pre>
 * &#064;Table("tb_user")
 * public class UserEntity extends BaseEntity {
 *     private String userName;   // 约定映射为 user_name
 * }
 * </pre>
 *
 * @author sombreknight
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

    /**
     * 表名
     */
    String value();

    /**
     * 主键列名，默认 "id"。
     * <p>当主键字段对应的数据库列名不是 id（如 uid）时，通过此属性指定。</p>
     */
    String idColumn() default "";
}
