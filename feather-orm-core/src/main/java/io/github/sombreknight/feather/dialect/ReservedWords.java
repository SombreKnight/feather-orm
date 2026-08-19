package io.github.sombreknight.feather.dialect;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 保守保留字集合：SQL 标准保留字 + 各主流数据库（MySQL / PostgreSQL / SQL Server / Oracle）
 * 常用扩展关键字。命中即按方言引用符包裹，避免普通标识符在个别数据库上因关键字报语法错误。
 *
 * <p>集合取各家并集，宁多勿漏（多引用一个普通列名无副作用，漏引用一个关键字则直接报错）。</p>
 *
 * @author sombreknight
 */
final class ReservedWords {

    private static final Set<String> WORDS = new HashSet<>(Arrays.asList(
            // SQL:2003 标准保留字
            "ALL", "AND", "ANY", "AS", "ASC", "BETWEEN", "BINARY", "BY", "CASE", "CAST", "CHECK",
            "COLLATE", "COLUMN", "CONSTRAINT", "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_TIME",
            "CURRENT_TIMESTAMP", "CURRENT_USER", "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP",
            "ELSE", "END", "ESCAPE", "EXISTS", "FALSE", "FETCH", "FOR", "FOREIGN", "FROM", "FULL",
            "GRANT", "GROUP", "HAVING", "IN", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN",
            "KEY", "LEFT", "LIKE", "LIMIT", "NATURAL", "NOT", "NULL", "OFFSET", "ON", "OR", "ORDER",
            "OUTER", "PRIMARY", "REFERENCES", "RIGHT", "ROWS", "SELECT", "SET", "TABLE", "THEN",
            "TRUE", "UNION", "UNIQUE", "UPDATE", "USING", "VALUES", "WHEN", "WHERE", "WITH",
            // MySQL 扩展
            "DATABASE", "INDEX", "EXPLAIN", "LOCK", "OPTIMIZE", "REPLACE", "SHOW", "SIGNED",
            "UNSIGNED", "ZEROFILL", "AUTO_INCREMENT", "DUPLICATE",
            // PostgreSQL 扩展
            "ANALYZE", "CURRENT_CATALOG", "CURRENT_ROLE", "CURRENT_SCHEMA", "DO", "ILIKE",
            "SIMILAR", "USER",
            // SQL Server 扩展
            "TOP", "IDENTITY", "PIVOT", "UNPIVOT", "ROWNUMBER", "OUTPUT",
            // Oracle 扩展
            "LEVEL", "ROWNUM", "ROWID", "PRIOR", "CONNECT", "START", "SYSDATE", "NEXTVAL",
            "CURRVAL", "MINUS", "SYNONYM", "SEQUENCE", "TRIGGER", "PROCEDURE", "FUNCTION",
            "PACKAGE", "GRANTEE", "LOB",
            // 达梦 / 通用
            "PLUS", "SIZE", "OPTION", "EXEC", "EXECUTE", "TRANSACTION", "COMMIT", "ROLLBACK",
            "PRIVILEGES", "SESSION_USER", "SYSTEM_USER", "INTERVAL", "INOUT", "OUT", "OVERLAPS",
            "SIMILARITY"
    ));

    private ReservedWords() {
    }

    static boolean isReserved(String identifier) {
        return identifier != null && WORDS.contains(identifier.toUpperCase());
    }
}
