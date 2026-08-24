package io.github.sombreknight.feather.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指定 DAO 绑定的数据源集群（多数据源支持）
 *
 * <pre>
 * &#064;Repository
 * &#064;FeatherDataSource("order")      // 绑定 feather.datasource.others.order 集群
 * public class OrderDAO extends BaseDAO&lt;OrderEntity&gt; {
 * }
 * </pre>
 *
 * <p>不标注时走默认集群（feather.datasource.primary 或 others.default）。
 * 集群名不存在时启动期 fail-fast（BeanCreationException），避免运行时跑错库。</p>
 *
 * @author sombreknight
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatherDataSource {

    /**
     * 数据源集群名：feather.datasource.others 中的 key（或 default / primary）
     */
    String value();
}
