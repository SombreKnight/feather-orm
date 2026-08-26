package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SnowflakeIdGenerator}：workerId 校验 / 时钟回拨 / 并发唯一性 / ID 结构。
 */
class SnowflakeIdGeneratorTest {

    @Test
    void rejectsInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32));
        assertNotNull(new SnowflakeIdGenerator(0));
        assertNotNull(new SnowflakeIdGenerator(31));
    }

    @Test
    void defaultConstructorProducesIncreasingIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        long first = generator.nextId();
        long second = generator.nextId();
        assertTrue(first > 0, "id 必须为正数");
        assertTrue(second > first, "时间推进下 id 应单调递增");
    }

    @Test
    void clockRollbackThrowsIllegalState() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        generator.nextId();
        // 模拟时钟回拨：lastTimestamp 推进到未来
        Field field = SnowflakeIdGenerator.class.getDeclaredField("lastTimestamp");
        field.setAccessible(true);
        field.setLong(generator, System.currentTimeMillis() + 10_000);

        assertThrows(IllegalStateException.class, generator::nextId);
    }

    @Test
    void concurrentGenerationIsUnique() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7);
        int threads = 8;
        int perThread = 5000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        ids.add(generator.nextId());
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发生成应在 30s 内完成");
        assertEquals(0, errors.get());
        assertEquals(threads * perThread, ids.size(), "40000 个 id 必须全部唯一");
    }

    @Test
    void idEncodesWorkerIdInReservedBits() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(31);
        long id = generator.nextId();
        // 结构：时间戳(41) << 17 | workerId(5) << 12 | sequence(12)
        long worker = (id >> 12) & 31L;
        assertEquals(31L, worker);
    }

    @Test
    void rapidGenerationWithinSameMillisecondDoesNotCollide() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3);
        Set<Long> ids = new HashSet<>();
        // 单线程快速生成（大概率落在同一毫秒，验证序列号回绕不重复）
        for (int i = 0; i < 100_000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "同一毫秒内序列号回绕不应产生重复 id（iteration=" + i + "）");
        }
    }
}
