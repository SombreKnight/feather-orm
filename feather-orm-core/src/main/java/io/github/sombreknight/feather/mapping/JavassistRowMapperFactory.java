package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.exception.FeatherDaoException;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Javassist 字节码 RowMapper 工厂（默认）
 *
 * <p>运行时为目标实体生成 <code>XxxFeatherRowMapper</code> 类，mapRow 直接调用
 * 预解析好的 {@link FieldHandler}，避免逐行反射，同时彻底摆脱对包名的硬编码依赖
 * （生成代码中的类名全部通过 <code>Class.getName()</code> 在生成期拼接）。</p>
 *
 * <p>生成的类按类名静态缓存：多个 JdbcDAO / RowMapperSupport 实例映射同一实体时复用，
 * 避免重复定义同名字节码类导致 {@link LinkageError}。DO 与 DTO 模式使用不同类名后缀，互不干扰。</p>
 *
 * @author sombreknight
 */
public class JavassistRowMapperFactory implements RowMapperFactory {

    /** 已生成的 RowMapper 类缓存（跨 RowMapperSupport 实例共享） */
    private static final ConcurrentMap<String, Class<?>> GENERATED_CLASSES = new ConcurrentHashMap<>();

    @Override
    public <T> RowMapper<T> createRowMapper(Class<T> clazz, FieldHandler[] handlers, boolean dto) {
        try {
            String className = dto
                    ? clazz.getName() + "FeatherDtoRowMapper"
                    : clazz.getName() + "FeatherRowMapper";
            Class<?> generatedClass = GENERATED_CLASSES.computeIfAbsent(className,
                    k -> generateRowMapperClass(clazz, className, dto, handlers));
            Object instance = generatedClass.getConstructor(FieldHandler[].class).newInstance((Object) handlers);
            return (RowMapper<T>) instance;
        } catch (Exception e) {
            throw new FeatherDaoException("为实体[" + clazz.getName() + "]生成 RowMapper 失败", e);
        }
    }

    private static Class<?> generateRowMapperClass(Class<?> clazz, String className, boolean dto,
                                                   FieldHandler[] sampleHandlers) {
        try {
            javassist.ClassPool pool = new javassist.ClassPool();
            pool.appendSystemPath();
            pool.insertClassPath(new javassist.LoaderClassPath(clazz.getClassLoader()));

            javassist.CtClass ctClass = pool.makeClass(className);
            ctClass.setModifiers(Modifier.PUBLIC);
            ctClass.addInterface(pool.get(RowMapper.class.getName()));
            ctClass.addField(javassist.CtField.make(
                    "private final " + FieldHandler.class.getName() + "[] handlers;", ctClass));
            ctClass.addConstructor(javassist.CtNewConstructor.make(
                    "public " + simpleName(className) + "(" + FieldHandler.class.getName() + "[] handlers) { this.handlers = handlers; }",
                    ctClass));
            ctClass.addMethod(javassist.CtNewMethod.make(buildMapRowBody(clazz, sampleHandlers, dto), ctClass));

            // 显式声明 Java 8 字节码版本，保证在低版本 JVM 上可用
            try {
                ctClass.getClassFile2().setMajorVersion(52);
            } catch (Throwable ignore) {
                // 忽略版本设置失败，使用 javassist 默认版本
            }

            return defineGeneratedClass(clazz, ctClass);
        } catch (Exception e) {
            throw new FeatherDaoException("为实体[" + clazz.getName() + "]生成 RowMapper 类失败", e);
        }
    }

    /**
     * 定义生成的 RowMapper 类
     *
     * <p>Java 9+：使用 javassist 的 {@code toClass(Class)}（内部走 MethodHandles.privateLookupIn +
     * Lookup.defineClass），无需 --add-opens，兼容 JDK 17/21 模块系统；</p>
     * <p>Java 8：回退 javassist 原生 ClassLoader.defineClass 路径。</p>
     */
    private static Class<?> defineGeneratedClass(Class<?> referenceClass, javassist.CtClass ctClass)
            throws javassist.CannotCompileException {
        if (JAVA_9_PLUS) {
            return ctClass.toClass(referenceClass);
        }
        return ctClass.toClass(referenceClass.getClassLoader(), null);
    }

    /**
     * JDK 9+ 检测（Class.getModule 为 JDK 9 新增 API，存在即 9+）
     */
    private static final boolean JAVA_9_PLUS;

    static {
        boolean detected = false;
        try {
            Class.class.getMethod("getModule");
            detected = true;
        } catch (NoSuchMethodException ignore) {
            detected = false;
        }
        JAVA_9_PLUS = detected;
    }

    private static String simpleName(String className) {
        int idx = className.lastIndexOf('.');
        return idx < 0 ? className : className.substring(idx + 1);
    }

    /**
     * 生成 mapRow 方法体
     */
    private static String buildMapRowBody(Class<?> clazz, FieldHandler[] handlers, boolean dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("public Object mapRow(java.sql.ResultSet rs, int index) throws java.sql.SQLException {\n");
        sb.append("    ").append(clazz.getName()).append(" entity = new ").append(clazz.getName()).append("();\n");

        for (int i = 0; i < handlers.length; i++) {
            FieldHandler handler = handlers[i];
            java.lang.reflect.Field field = handler.getMeta().getField();
            String expr = "this.handlers[" + i + "].fromResultSet(rs)";

            sb.append("    try {\n");
            String setter = findSetter(clazz, field);
            if (setter != null) {
                if (field.getType().isPrimitive()) {
                    sb.append("        entity.").append(setter).append("(")
                            .append(unbox(field.getType(), expr)).append(");\n");
                } else {
                    sb.append("        entity.").append(setter).append("((")
                            .append(typeName(field.getType())).append(") ").append(expr).append(");\n");
                }
            } else {
                sb.append("        ").append(FieldAccessSupport.class.getName())
                        .append(".setFieldValue(entity, \"").append(field.getName())
                        .append("\", ").append(expr).append(");\n");
            }
            sb.append("    } catch (java.sql.SQLException e) {\n");
            if (dto) {
                sb.append("        // 结果集中不存在该列，跳过: ").append(field.getName()).append("\n");
            } else {
                sb.append("        throw new java.sql.SQLException(\"Feather 实体映射失败: 列[")
                        .append(handler.getColumn()).append("] 实体[").append(clazz.getName())
                        .append("]\", e);\n");
            }
            sb.append("    }\n");
        }

        sb.append("    return entity;\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String findSetter(Class<?> clazz, java.lang.reflect.Field field) {
        String setterName = "set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        try {
            Method method = clazz.getMethod(setterName, field.getType());
            return method.getName();
        } catch (NoSuchMethodException ignore) {
            return null;
        }
    }

    private static final Map<Class<?>, String> UNBOX_METHODS = new HashMap<>();

    static {
        UNBOX_METHODS.put(int.class, "intValue");
        UNBOX_METHODS.put(long.class, "longValue");
        UNBOX_METHODS.put(short.class, "shortValue");
        UNBOX_METHODS.put(byte.class, "byteValue");
        UNBOX_METHODS.put(double.class, "doubleValue");
        UNBOX_METHODS.put(float.class, "floatValue");
        UNBOX_METHODS.put(boolean.class, "booleanValue");
        UNBOX_METHODS.put(char.class, "charValue");
    }

    /**
     * 生成原始类型拆箱表达式：((java.lang.Long) expr).longValue()
     */
    private static String unbox(Class<?> primitiveType, String expr) {
        String wrapper = boxedName(primitiveType);
        return "((" + wrapper + ") " + expr + ")." + UNBOX_METHODS.get(primitiveType) + "()";
    }

    private static String boxedName(Class<?> primitiveType) {
        if (primitiveType == int.class) return Integer.class.getName();
        if (primitiveType == long.class) return Long.class.getName();
        if (primitiveType == short.class) return Short.class.getName();
        if (primitiveType == byte.class) return Byte.class.getName();
        if (primitiveType == double.class) return Double.class.getName();
        if (primitiveType == float.class) return Float.class.getName();
        if (primitiveType == boolean.class) return Boolean.class.getName();
        if (primitiveType == char.class) return Character.class.getName();
        return primitiveType.getName();
    }

    private static String typeName(Class<?> type) {
        String canonical = type.getCanonicalName();
        return canonical != null ? canonical : type.getName();
    }
}
