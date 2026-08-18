package io.github.sombreknight.feather.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.sombreknight.feather.exception.FeatherDaoException;

import java.lang.reflect.Type;
import java.util.List;

/**
 * JSON 工具（内部使用）
 *
 * @author sombreknight
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private JsonUtils() {
    }

    /**
     * 对象转 JSON 字符串
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new FeatherDaoException("对象转 JSON 失败: " + obj.getClass().getName(), e);
        }
    }

    /**
     * JSON 转对象
     */
    public static <T> T toObject(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new FeatherDaoException("JSON 转对象失败: " + clazz.getName(), e);
        }
    }

    /**
     * JSON 转对象（支持泛型，如 List&lt;String&gt;、Map&lt;String,Object&gt;）
     *
     * @param json JSON 字符串
     * @param type 目标类型（字段的泛型类型，如 List&lt;String&gt;）
     */
    public static Object toObject(String json, Type type) {
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructType(type));
        } catch (Exception e) {
            throw new FeatherDaoException("JSON 转对象失败: " + type.getTypeName(), e);
        }
    }

    /**
     * JSON 转 List
     */
    public static <T> List<T> toGenericList(String json, Class<T> elementClass) {
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructParametricType(List.class, elementClass);
            return MAPPER.readValue(json, javaType);
        } catch (Exception e) {
            throw new FeatherDaoException("JSON 转 List 失败: " + elementClass.getName(), e);
        }
    }

    /**
     * JSON 转对象（TypeReference 形式）
     */
    public static <T> T toObject(String json, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new FeatherDaoException("JSON 转对象失败: " + typeReference.getType().getTypeName(), e);
        }
    }
}
