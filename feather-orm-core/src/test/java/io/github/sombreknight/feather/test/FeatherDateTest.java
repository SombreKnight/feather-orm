package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.util.FeatherDate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FeatherDate 单元测试
 *
 * @author sombreknight
 */
public class FeatherDateTest {

    private static final long MILLIS = 1700000000000L; // 2023-11-14 22:13:20 (UTC)

    /** 按系统默认时区格式化（FeatherDate 使用系统时区，断言须与时区无关） */
    private static String formatDateTime(long millis) {
        return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis),
                        java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static java.time.LocalDateTime toLocalDateTime(long millis) {
        return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis),
                java.time.ZoneId.systemDefault());
    }

    @Test
    public void constructors() {
        assertEquals(MILLIS, new FeatherDate(MILLIS).getTime());
        assertEquals(MILLIS, new FeatherDate(new Date(MILLIS)).getTime());
        assertEquals(MILLIS, new FeatherDate(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(MILLIS), java.time.ZoneId.systemDefault())).getTime());
        assertEquals(MILLIS, FeatherDate.of(MILLIS).getTime());
        assertEquals(MILLIS, FeatherDate.of(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(MILLIS), java.time.ZoneId.systemDefault())).getTime());
        assertNotNull(FeatherDate.now());
        assertNotNull(new FeatherDate());
    }

    @Test
    public void ofLocalDateUsesStartOfDay() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        FeatherDate fd = FeatherDate.of(date);
        assertEquals(date, fd.toLocalDate());
        assertEquals(java.time.LocalTime.MIN, fd.toLocalDateTime().toLocalTime());
    }

    @Test
    public void zeroTime() {
        assertTrue(FeatherDate.ZERO_INST.isZeroTime());
        assertFalse(new FeatherDate(MILLIS).isZeroTime());
        assertEquals("0000-00-00 00:00:00", FeatherDate.ZERO_INST.datetimeString());
        assertEquals("0000-00-00", FeatherDate.ZERO_INST.dateString());
        assertEquals("00:00:00", FeatherDate.ZERO_INST.timeString());
    }

    @Test
    public void format() {
        FeatherDate fd = new FeatherDate(MILLIS);
        assertEquals(formatDateTime(MILLIS), fd.datetimeString());
        assertEquals(formatDateTime(MILLIS).substring(0, 10), fd.dateString());
        assertEquals(formatDateTime(MILLIS).substring(11), fd.timeString());
    }

    @Test
    public void timeSecond() {
        assertEquals(MILLIS / 1000L, new FeatherDate(MILLIS).getTimeSecond());
    }

    @Test
    public void conversions() {
        FeatherDate fd = new FeatherDate(MILLIS);
        assertEquals(toLocalDateTime(MILLIS).toLocalDate(), fd.toLocalDate());
        assertEquals(toLocalDateTime(MILLIS), fd.toLocalDateTime());
        assertEquals(toLocalDateTime(MILLIS).toLocalTime(), fd.toLocalTime());
        assertEquals(MILLIS, fd.toDate().getTime());
    }

    @Test
    public void equalsAndHashCode() {
        assertEquals(new FeatherDate(MILLIS), new FeatherDate(MILLIS));
        assertEquals(new FeatherDate(MILLIS).hashCode(), new FeatherDate(MILLIS).hashCode());
        assertNotEquals(new FeatherDate(MILLIS), new FeatherDate(MILLIS + 1));
        assertNotEquals(FeatherDate.ZERO_INST, new FeatherDate(0L));
        assertNotEquals(null, FeatherDate.ZERO_INST);
    }

    @Test
    public void compareTo() {
        FeatherDate a = new FeatherDate(1000L);
        FeatherDate b = new FeatherDate(2000L);
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(new FeatherDate(1000L)));
    }

    @Test
    public void toStringUsesDatetime() {
        assertEquals(formatDateTime(MILLIS), new FeatherDate(MILLIS).toString());
    }
}
