package io.github.sombreknight.feather.core;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的字段函数式接口：以方法引用（Lambda）方式引用实体字段，
 * 供 QueryHelper 在编译期获得字段名，替代字符串字面量。
 *
 * <pre>
 * dao.findList(dao.getQueryHelper()
 *         .whereEqual(UserEntity::getUserName, "张三"));
 * </pre>
 *
 * <p>实现必须为方法引用或等价 lambda（如 {@code UserEntity::getUserName}），
 * 运行时通过 {@link java.lang.invoke.SerializedLambda} 解析出 getter 方法名并推导字段名。</p>
 *
 * @param <T> 实体类型
 * @param <R> 字段（getter 返回）类型
 * @author sombreknight
 */
@FunctionalInterface
public interface FieldFunction<T, R> extends Function<T, R>, Serializable {
}
