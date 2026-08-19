package io.github.sombreknight.feather.dialect;

/**
 * 方言公共逻辑：标识符引用的三级判定与 count 包装时的尾部子句剥离。
 *
 * @author sombreknight
 */
public abstract class AbstractDialect implements SqlDialect {

    private final String name;
    private final String openQuote;
    private final String closeQuote;

    protected AbstractDialect(String name, String openQuote, String closeQuote) {
        this.name = name;
        this.openQuote = openQuote;
        this.closeQuote = closeQuote;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        if (isQuoted(identifier) || (isPlainIdentifier(identifier) && !ReservedWords.isReserved(identifier))) {
            return identifier;
        }
        return openQuote + identifier + closeQuote;
    }

    /**
     * 是否已带引用符（反引号 / 双引号 / 方括号）——尊重用户显式指定的引用形式
     */
    protected static boolean isQuoted(String identifier) {
        return (identifier.startsWith("`") && identifier.endsWith("`"))
                || (identifier.startsWith("\"") && identifier.endsWith("\""))
                || (identifier.startsWith("[") && identifier.endsWith("]"));
    }

    /**
     * 是否为合法普通标识符：[A-Za-z_][A-Za-z0-9_]*
     */
    protected static boolean isPlainIdentifier(String identifier) {
        if (identifier.isEmpty()) {
            return false;
        }
        char first = identifier.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }
        for (int i = 1; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 剥离查询尾部对 count 无意义的子句：从后向前扫描最外层（括号深度 0）的
     * {@code order by} / {@code for update}，取最早出现处并切掉其后内容。
     *
     * <p>如 {@code select ... where ... order by a desc for update} → {@code select ... where ...}</p>
     */
    static String stripTail(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        int depth = 0;
        int cut = -1;
        for (int i = sql.length() - 1; i >= 0; i--) {
            char c = sql.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
            } else if (depth == 0) {
                if (matchesKeyword(sql, i, "order by") || matchesKeyword(sql, i, "for update")) {
                    cut = i;
                }
            }
        }
        return cut < 0 ? sql : sql.substring(0, cut).trim();
    }

    /**
     * 判断 sql 的 index 位置（含）开始是否出现 keyword，且前后均为空白或边界（避免误伤 like '%order by%' 类参数）
     */
    private static boolean matchesKeyword(String sql, int index, String keyword) {
        if (index + keyword.length() > sql.length()) {
            return false;
        }
        if (index > 0 && !Character.isWhitespace(sql.charAt(index - 1))) {
            return false;
        }
        if (index + keyword.length() < sql.length()
                && !Character.isWhitespace(sql.charAt(index + keyword.length()))) {
            return false;
        }
        return sql.regionMatches(true, index, keyword, 0, keyword.length());
    }
}
