package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.annotation.Column;
import io.github.sombreknight.feather.annotation.EnumValue;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.SqlDialect;
import io.github.sombreknight.feather.util.NamingUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * 字段映射元数据：Java 字段 ↔ 数据库列
 *
 * @author sombreknight
 */
public class FieldMeta {

    private final Field field;
    private final Class<?> javaType;
    private final Type genericType;
    private final String column;
    private final String quotedColumn;
    private final Column columnAnnotation;
    private final EnumValue enumValueAnnotation;

    public FieldMeta(Field field, String column) {
        this(field, column, DialectRegistry.defaultDialect());
    }

    /**
     * @param dialect 当前数据库方言（决定列名引用形式）
     */
    public FieldMeta(Field field, String column, SqlDialect dialect) {
        this(field, column, dialect, null);
    }

    /**
     * @param dialect           当前数据库方言（决定列名引用形式）
     * @param javaTypeOverride  Java 类型覆盖（可为 null；泛型基类字段如 {@code BaseEntity&lt;ID&gt;.id}
     *                          擦除为 {@code Object} 时，传入泛型实参还原真实类型）
     */
    public FieldMeta(Field field, String column, SqlDialect dialect, Class<?> javaTypeOverride) {
        this.field = field;
        this.javaType = javaTypeOverride != null ? javaTypeOverride : field.getType();
        this.genericType = field.getGenericType();
        this.column = column;
        this.quotedColumn = dialect.quoteIdentifier(column);
        this.columnAnnotation = field.getAnnotation(Column.class);
        this.enumValueAnnotation = field.getAnnotation(EnumValue.class);
    }

    /**
     * 依据注解或约定构建字段元数据：
     * 有 {@link Column} 注解用注解值，否则走驼峰转下划线约定
     */
    public static FieldMeta of(Field field) {
        return of(field, DialectRegistry.defaultDialect());
    }

    /**
     * @param dialect 当前数据库方言（决定列名引用形式）
     */
    public static FieldMeta of(Field field, SqlDialect dialect) {
        Column column = field.getAnnotation(Column.class);
        String columnName = (column != null && !column.value().trim().isEmpty())
                ? column.value().trim()
                : NamingUtils.camelToSnake(field.getName());
        return new FieldMeta(field, columnName, dialect);
    }

    public Field getField() {
        return field;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    /**
     * 字段的泛型类型（用于 JSON 反序列化时还原 List&lt;String&gt; 等）
     */
    public Type getGenericType() {
        return genericType;
    }

    /**
     * 原始列名（无反引号）
     */
    public String getColumn() {
        return column;
    }

    /**
     * 反引号包裹的列名，用于拼接 SQL
     */
    public String getQuotedColumn() {
        return quotedColumn;
    }

    public Column getColumnAnnotation() {
        return columnAnnotation;
    }

    public EnumValue getEnumValueAnnotation() {
        return enumValueAnnotation;
    }
}
