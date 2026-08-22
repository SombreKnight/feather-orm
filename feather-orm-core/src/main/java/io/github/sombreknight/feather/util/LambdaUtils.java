package io.github.sombreknight.feather.util;

import io.github.sombreknight.feather.core.FieldFunction;
import io.github.sombreknight.feather.exception.FeatherDaoException;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lambda 字段引用解析工具：将 {@link FieldFunction} 方法引用解析为 Java 字段名。
 *
 * <p>原理：接口实现因继承 {@link java.io.Serializable}，编译生成的合成 lambda 类自带
 * {@code writeReplace()}，调用后返回 {@link SerializedLambda}，其中携带被引用方法的
 * 完整信息（实现类 + 方法名）。解析仅依赖方法名（如 {@code getUserName}），与实现类无关，
 * 因此父类字段（{@code UserEntity::getId}，getter 在 BaseEntity）与 CGLIB 代理类均天然兼容。</p>
 *
 * <p>解析结果以 {@code implClass#methodName} 为 key 缓存，避免高频反射开销。</p>
 *
 * @author sombreknight
 */
public final class LambdaUtils {

    private static final String PREFIX_GET = "get";
    private static final String PREFIX_IS = "is";
    private static final String PREFIX_SET = "set";

    private static final Map<String, String> FIELD_NAME_CACHE = new ConcurrentHashMap<>(64);

    private LambdaUtils() {
    }

    /**
     * 解析字段引用为 Java 字段名（fail-fast，非法引用立即抛异常）
     *
     * @param fieldFunction 方法引用，如 UserEntity::getUserName
     * @return Java 字段名，如 userName
     */
    public static String resolveFieldName(FieldFunction<?, ?> fieldFunction) {
        if (fieldFunction == null) {
            throw new FeatherDaoException("字段引用不能为 null");
        }
        SerializedLambda serializedLambda = toSerializedLambda(fieldFunction);
        String cacheKey = serializedLambda.getImplClass() + "#" + serializedLambda.getImplMethodName();
        return FIELD_NAME_CACHE.computeIfAbsent(cacheKey, key -> toFieldName(serializedLambda.getImplMethodName()));
    }

    private static SerializedLambda toSerializedLambda(FieldFunction<?, ?> fieldFunction) {
        try {
            Method writeReplace = fieldFunction.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object result = writeReplace.invoke(fieldFunction);
            if (result instanceof SerializedLambda) {
                return (SerializedLambda) result;
            }
            throw new FeatherDaoException("字段引用序列化信息异常：" + fieldFunction.getClass().getName());
        } catch (NoSuchMethodException e) {
            throw new FeatherDaoException("无法解析字段引用，请使用 getter 方法引用形式（如 UserEntity::getUserName）："
                    + fieldFunction.getClass().getName(), e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new FeatherDaoException("字段引用解析失败：" + fieldFunction.getClass().getName(), e);
        }
    }

    /**
     * getter 方法名 → 字段名：剥 get/is/set 前缀并首字母小写（getUserName → userName）
     */
    private static String toFieldName(String methodName) {
        String prefix = null;
        if (methodName.startsWith(PREFIX_GET) && methodName.length() > PREFIX_GET.length()) {
            prefix = PREFIX_GET;
        } else if (methodName.startsWith(PREFIX_IS) && methodName.length() > PREFIX_IS.length()) {
            prefix = PREFIX_IS;
        } else if (methodName.startsWith(PREFIX_SET) && methodName.length() > PREFIX_SET.length()) {
            prefix = PREFIX_SET;
        }
        if (prefix == null) {
            throw new FeatherDaoException("无法从方法名 [" + methodName
                    + "] 推导字段名，请使用 getter 方法引用形式（如 UserEntity::getUserName）");
        }
        String property = methodName.substring(prefix.length());
        return Character.toLowerCase(property.charAt(0)) + property.substring(1);
    }
}
