package io.github.sombreknight.feather.mapping;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 字段读写访问器解析：在 RowMapper 构建期把字段访问解析为 MethodHandle，
 * 运行时零反射、零 AccessibleObject native 调用。
 *
 * <p>解析优先级（与 Javassist 模式的 findSetter 对齐）：</p>
 * <ol>
 *   <li>public setter/getter（如 {@code setUserName} / {@code getUserName}，boolean 兼容 {@code isXxx}）→ publicLookup；</li>
 *   <li>字段直接访问 → {@link MethodHandles#privateLookupIn} + {@code unreflectSetter/unreflectGetter}
 *       （JDK 9+，对 classpath 实体无需 --add-opens）；</li>
 *   <li>兜底：一次性 {@code setAccessible(true)} 后使用 {@link Field#set/get}（不再逐行 toggle）。</li>
 * </ol>
 *
 * @author sombreknight
 */
final class FieldAccessResolver {

    /** 统一 MethodHandle 签名：(Object)Object（getter）与 (Object,Object)void（setter） */
    private static final MethodType GETTER_TYPE = MethodType.methodType(Object.class, Object.class);
    private static final MethodType SETTER_TYPE = MethodType.methodType(void.class, Object.class, Object.class);

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private FieldAccessResolver() {
    }

    /**
     * 字段访问器：getter/setter MethodHandle（可为 null，null 时调用方回退 Field 直读直写）。
     * {@code override} 表示已一次性 setAccessible(true)（回退路径可直接 Field.set/get）。
     */
    static final class Accessor {
        final MethodHandle getter;
        final MethodHandle setter;
        final boolean override;

        Accessor(MethodHandle getter, MethodHandle setter, boolean override) {
            this.getter = getter;
            this.setter = setter;
            this.override = override;
        }
    }

    static Accessor resolve(Class<?> clazz, Field field) {
        MethodHandle getter = resolveGetter(clazz, field);
        MethodHandle setter = resolveSetter(clazz, field);
        boolean override = false;
        if ((getter == null || setter == null) && !field.isAccessible()) {
            // 存在回退路径时，构建期一次性 setAccessible(true)，消除逐行 native toggle
            field.setAccessible(true);
            override = true;
        }
        return new Accessor(getter, setter, override);
    }

    private static MethodHandle resolveSetter(Class<?> clazz, Field field) {
        String setterName = "set" + capitalize(field.getName());
        try {
            Method setter = clazz.getMethod(setterName, field.getType());
            if (Modifier.isStatic(setter.getModifiers())) {
                return null;
            }
            MethodHandle mh = MethodHandles.publicLookup().unreflect(setter);
            return mh.asType(SETTER_TYPE);
        } catch (NoSuchMethodException | IllegalAccessException ignore) {
            // 无 public setter，回退字段直写
        }
        try {
            MethodHandle mh = MethodHandles.privateLookupIn(clazz, LOOKUP).unreflectSetter(field);
            return mh.asType(SETTER_TYPE);
        } catch (IllegalAccessException ignore) {
            return null; // 兜底 Field.set（调用方已一次性 setAccessible）
        }
    }

    private static MethodHandle resolveGetter(Class<?> clazz, Field field) {
        String getterName = "get" + capitalize(field.getName());
        MethodHandle fromGetter = tryPublicGetter(clazz, getterName);
        if (fromGetter != null) {
            return fromGetter;
        }
        if (field.getType() == boolean.class) {
            MethodHandle fromIs = tryPublicGetter(clazz, "is" + capitalize(field.getName()));
            if (fromIs != null) {
                return fromIs;
            }
        }
        try {
            MethodHandle mh = MethodHandles.privateLookupIn(clazz, LOOKUP).unreflectGetter(field);
            return mh.asType(GETTER_TYPE);
        } catch (IllegalAccessException ignore) {
            return null; // 兜底 Field.get（调用方已一次性 setAccessible）
        }
    }

    private static MethodHandle tryPublicGetter(Class<?> clazz, String getterName) {
        try {
            Method getter = clazz.getMethod(getterName);
            if (Modifier.isStatic(getter.getModifiers())) {
                return null;
            }
            MethodHandle mh = MethodHandles.publicLookup().unreflect(getter);
            return mh.asType(GETTER_TYPE);
        } catch (NoSuchMethodException | IllegalAccessException ignore) {
            return null;
        }
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
