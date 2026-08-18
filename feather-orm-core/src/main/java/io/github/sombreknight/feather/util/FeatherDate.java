package io.github.sombreknight.feather.util;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

/**
 * Feather 日期类型
 *
 * <p>对标原 HaoDate 的使用习惯，但不再继承 {@link Date}（避免可变性、序列化等坑），
 * 内部持有 epoch 毫秒值，支持 MySQL 零时间（0000-00-00 00:00:00）表达。</p>
 *
 * <p>API 保持顺手：{@link #getTime()}、{@link #isZeroTime()}、{@link #datetimeString()}、
 * {@link #getTimeSecond()}、{@link #ZERO_INST}。</p>
 *
 * @author sombreknight
 */
public class FeatherDate implements Serializable, Comparable<FeatherDate> {

    private static final long serialVersionUID = 1L;

    /** 零时间实例（不可变） */
    public static final FeatherDate ZERO_INST = new FeatherDate(0L, true);

    private static final String ZERO_DATETIME = "0000-00-00 00:00:00";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final long epochMillis;
    private final boolean zero;

    private FeatherDate(long epochMillis, boolean zero) {
        this.epochMillis = epochMillis;
        this.zero = zero;
    }

    public FeatherDate() {
        this(System.currentTimeMillis());
    }

    public FeatherDate(long epochMillis) {
        this(epochMillis, false);
    }

    public FeatherDate(Date date) {
        this(date == null ? 0L : date.getTime());
    }

    public FeatherDate(LocalDateTime localDateTime) {
        this(localDateTime == null ? 0L
                : localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public static FeatherDate now() {
        return new FeatherDate();
    }

    public static FeatherDate of(long epochMillis) {
        return new FeatherDate(epochMillis);
    }

    public static FeatherDate of(LocalDateTime localDateTime) {
        return new FeatherDate(localDateTime);
    }

    public static FeatherDate of(LocalDate localDate) {
        return localDate == null ? null
                : new FeatherDate(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    /**
     * 是否为零时间（0000-00-00 00:00:00）
     */
    public boolean isZeroTime() {
        return zero;
    }

    /**
     * epoch 毫秒值
     */
    public long getTime() {
        return epochMillis;
    }

    /**
     * epoch 秒值
     */
    public long getTimeSecond() {
        return epochMillis / 1000L;
    }

    /**
     * yyyy-MM-dd HH:mm:ss；零时间返回 0000-00-00 00:00:00
     */
    public String datetimeString() {
        if (zero) {
            return ZERO_DATETIME;
        }
        return DATETIME_FORMATTER.format(toLocalDateTime());
    }

    /**
     * yyyy-MM-dd；零时间返回 0000-00-00
     */
    public String dateString() {
        if (zero) {
            return "0000-00-00";
        }
        return DATE_FORMATTER.format(toLocalDateTime());
    }

    /**
     * HH:mm:ss；零时间返回 00:00:00
     */
    public String timeString() {
        if (zero) {
            return "00:00:00";
        }
        return TIME_FORMATTER.format(toLocalDateTime());
    }

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    public LocalDate toLocalDate() {
        return toLocalDateTime().toLocalDate();
    }

    public LocalTime toLocalTime() {
        return toLocalDateTime().toLocalTime();
    }

    public Date toDate() {
        return new Date(epochMillis);
    }

    @Override
    public int compareTo(FeatherDate other) {
        if (other == null) {
            return 1;
        }
        return Long.compare(epochMillis, other.epochMillis);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeatherDate)) {
            return false;
        }
        FeatherDate that = (FeatherDate) o;
        return epochMillis == that.epochMillis && zero == that.zero;
    }

    @Override
    public int hashCode() {
        return Objects.hash(epochMillis, zero);
    }

    @Override
    public String toString() {
        return datetimeString();
    }
}
