package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.exception.FeatherDaoException;
import io.github.sombreknight.feather.util.ReflectUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据库表与实体的列映射关系，以及静态 SQL 模板
 *
 * <p>列名解析规则：{@code @Column} 注解值优先，否则驼峰转下划线约定；
 * 主键列优先使用 {@code @Table.idColumn()}，否则 {@code id} 字段自身的映射。</p>
 *
 * @author sombreknight
 */
public class ColumnMapper<T extends BaseEntity> {

    private static final String PK_FIELD_NAME = "id";

    private final Class<T> clazz;
    private final String tableName;
    private final String quotedTableName;
    private final String idColumn;
    private final String quotedIdColumn;
    private final List<FieldMeta> fieldMetas;

    private final String fromSql;
    private final String countSql;
    private final String deleteSql;

    ColumnMapper(Class<T> clazz) {
        this.clazz = clazz;
        Table table = clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new FeatherDaoException("实体[" + clazz.getName() + "]缺少 @Table 注解");
        }
        this.tableName = table.value().trim();
        this.quotedTableName = quote(tableName);

        // 收集映射字段
        List<FieldMeta> metas = new ArrayList<>();
        String tableIdColumn = table.idColumn() == null ? "" : table.idColumn().trim();
        for (Field field : ReflectUtils.findFields(clazz, true)) {
            if (!ReflectUtils.isMappable(field) || Modifier.isVolatile(field.getModifiers())) {
                continue;
            }
            FieldMeta meta = FieldMeta.of(field);
            if (PK_FIELD_NAME.equals(field.getName()) && !tableIdColumn.isEmpty()) {
                // 主键列名由 @Table.idColumn 指定
                meta = new FieldMeta(field, tableIdColumn);
            }
            metas.add(meta);
        }
        this.fieldMetas = Collections.unmodifiableList(metas);

        // 主键列
        String resolvedIdColumn = tableIdColumn;
        if (resolvedIdColumn.isEmpty()) {
            for (FieldMeta meta : fieldMetas) {
                if (PK_FIELD_NAME.equals(meta.getField().getName())) {
                    resolvedIdColumn = meta.getColumn();
                    break;
                }
            }
        }
        if (resolvedIdColumn.isEmpty()) {
            throw new FeatherDaoException("实体[" + clazz.getName() + "]未找到主键字段[" + PK_FIELD_NAME + "]");
        }
        this.idColumn = resolvedIdColumn;
        this.quotedIdColumn = quote(resolvedIdColumn);

        this.fromSql = " select jdbc_x.* from " + quotedTableName + " jdbc_x ";
        this.countSql = " select count(*) from " + quotedTableName + " jdbc_x ";
        this.deleteSql = " delete from " + quotedTableName + " ";
    }

    private static String quote(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.startsWith("`") && name.endsWith("`")) {
            return name;
        }
        return "`" + name + "`";
    }

    public Class<T> getClazz() {
        return clazz;
    }

    /**
     * 原始表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 反引号表名
     */
    public String getQuotedTableName() {
        return quotedTableName;
    }

    /**
     * 原始主键列名
     */
    public String getIdColumn() {
        return idColumn;
    }

    /**
     * 反引号主键列名
     */
    public String getQuotedIdColumn() {
        return quotedIdColumn;
    }

    public List<FieldMeta> getFieldMetas() {
        return fieldMetas;
    }

    /**
     * 按 Java 字段名查找列名；找不到返回 null
     */
    public String getColumn(String fieldName) {
        for (FieldMeta meta : fieldMetas) {
            if (meta.getField().getName().equals(fieldName)) {
                return meta.getColumn();
            }
        }
        return null;
    }

    public String getFromSql() {
        return fromSql;
    }

    public String getCountSql() {
        return countSql;
    }

    public String getDeleteSql() {
        return deleteSql;
    }
}
