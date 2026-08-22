package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.UuidIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UuidIdGenerator 单元测试
 *
 * @author sombreknight
 */
public class UuidIdGeneratorTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final UuidIdGenerator generator = new UuidIdGenerator();

    @Test
    public void idTypeIsString() {
        assertEquals(String.class, generator.idType());
    }

    @Test
    public void nextIdIsStandardUuid() {
        String id = generator.nextId();
        assertNotNull(id);
        assertTrue(UUID_PATTERN.matcher(id).matches(), "应生成标准 36 位 UUID 字符串: " + id);
    }

    @Test
    public void nextIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            ids.add(generator.nextId());
        }
        assertEquals(10000, ids.size(), "10000 个 UUID 应全部唯一");
    }
}
