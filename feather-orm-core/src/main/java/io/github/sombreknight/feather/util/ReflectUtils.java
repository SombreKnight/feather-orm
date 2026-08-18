package io.github.sombreknight.feather.util;

import io.github.sombreknight.feather.exception.FeatherDaoException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 反射工具类
 *
 * @author sombreknight
 */
public final class ReflectUtils {

    private ReflectUtils() {
    }

    /**
     * 查找成员变量（含父类）
     *
     * @param clazz              类型
     * @param fieldName          成员变量名
     * @param includeParentClass 是否包含父类
     * @return 成员变量
     */
    public static Field findField(Class<?> clazz, String fieldName, boolean includeParentClass) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ignore) {
            // continue
        }
        if (field != null) {
            return field;
        }
        if (includeParentClass && clazz.getSuperclass() != null) {
            return findField(clazz.getSuperclass(), fieldName, true);
        }
        return null;
    }

    /**
     * 查找成员变量集合（子类优先，父类去重后追加）
     *
     * @param type              类型
     * @param includeParentClass 是否包含父类
     * @return 成员变量数组
     */
    public static Field[] findFields(Class<?> type, boolean includeParentClass) {
        List<Field> fieldList = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();
        collectFields(type, includeParentClass, fieldList, fieldNames);
        return fieldList.toArray(new Field[0]);
    }

    private static void collectFields(Class<?> type, boolean includeParentClass,
                                      List<Field> fieldList, Set<String> fieldNames) {
        if (type == null || type == Object.class) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            if (fieldNames.add(field.getName())) {
                fieldList.add(field);
            }
        }
        if (includeParentClass) {
            collectFields(type.getSuperclass(), true, fieldList, fieldNames);
        }
    }

    /**
     * 读取成员变量值
     *
     * @param field  成员变量
     * @param target 目标对象
     * @return 值
     */
    public static Object getFieldValue(Field field, Object target) {
        try {
            boolean accessible = field.isAccessible();
            if (!accessible) {
                field.setAccessible(true);
            }
            Object value = field.get(target);
            if (!accessible) {
                field.setAccessible(false);
            }
            return value;
        } catch (IllegalAccessException e) {
            throw new FeatherDaoException("读取字段[" + field.getName() + "]值失败", e);
        }
    }

    /**
     * 写入成员变量值
     *
     * @param field  成员变量
     * @param target 目标对象
     * @param value  值
     */
    public static void setFieldValue(Field field, Object target, Object value) {
        try {
            boolean accessible = field.isAccessible();
            if (!accessible) {
                field.setAccessible(true);
            }
            field.set(target, value);
            if (!accessible) {
                field.setAccessible(false);
            }
        } catch (IllegalAccessException e) {
            throw new FeatherDaoException("写入字段[" + field.getName() + "]值失败", e);
        }
    }

    /**
     * 判断是否为可映射字段（排除 static 与 transient）
     */
    public static boolean isMappable(Field field) {
        return !Modifier.isStatic(field.getModifiers())
                && !Modifier.isTransient(field.getModifiers());
    }
}
