package io.github.sombreknight.feather.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机数工具（内部使用）
 *
 * @author sombreknight
 */
public final class RandomUtils {

    private RandomUtils() {
    }

    /**
     * 生成 [min, max] 闭区间的随机整数
     */
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(max - min + 1) + min;
    }
}
