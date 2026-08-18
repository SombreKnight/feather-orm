package io.github.sombreknight.feather.mapping;

import io.github.sombreknight.feather.type.TypeHandler;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字段处理器：字段元数据 + 类型处理器 + 列名的组合
 *
 * <p>在 RowMapper 生成阶段预解析并缓存，运行时每行仅一次方法调用，无反射、无查表开销。</p>
 *
 * @author sombreknight
 */
public class FieldHandler {

    private final TypeHandler handler;
    private final FieldMeta meta;
    private final String column;

    public FieldHandler(TypeHandler handler, FieldMeta meta, String column) {
        this.handler = handler;
        this.meta = meta;
        this.column = column;
    }

    public Object fromResultSet(ResultSet rs) throws SQLException {
        return handler.fromResultSet(rs, column, meta);
    }

    public TypeHandler getHandler() {
        return handler;
    }

    public FieldMeta getMeta() {
        return meta;
    }

    public String getColumn() {
        return column;
    }
}
