package io.github.sombreknight.feather.util;

/**
 * 命名转换工具
 *
 * @author sombreknight
 */
public final class NamingUtils {

    private NamingUtils() {
    }

    /**
     * 驼峰转下划线：userName → user_name，userId → user_id，userURL → user_url
     *
     * @param camelCase 驼峰命名
     * @return 下划线命名
     */
    public static String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        return camelCase
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase();
    }
}
