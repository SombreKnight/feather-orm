package io.github.sombreknight.feather.type;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.SqlParam;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link TemporalTypeHandler} java.time 类型 DB round-trip（H2）。
 */
class TemporalTypeHandlerTest {

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;

    @BeforeAll
    public static void init() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:feather-temporal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        new JdbcTemplate(dataSource).execute("CREATE TABLE tb_temporal (" +
                "id BIGINT PRIMARY KEY," +
                "d_date DATE," +
                "d_date_time DATETIME," +
                "d_instant TIMESTAMP," +
                "d_offset TIMESTAMP" +
                ")");

        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        RowMapperSupport support = new RowMapperSupport(registry, new JavassistRowMapperFactory());
        jdbcDAO = new JdbcDAO(new NamedParameterJdbcTemplate(dataSource), new FixedIdGenerator(), support, 0);
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    public void javaTimeRoundTrip() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        LocalDateTime dateTime = LocalDateTime.of(2026, 8, 26, 14, 30, 0);
        Instant instant = Instant.ofEpochMilli(1700000000000L); // 毫秒精度（H2 TIMESTAMP 无纳秒）
        OffsetDateTime offset = OffsetDateTime.of(2026, 8, 26, 14, 30, 0, 0, ZoneOffset.ofHours(8));

        TemporalEntity entity = new TemporalEntity();
        entity.setDDate(date);
        entity.setDDateTime(dateTime);
        entity.setDInstant(instant);
        entity.setDOffset(offset);

        jdbcDAO.save(entity);

        TemporalEntity found = jdbcDAO.findById(TemporalEntity.class, entity.getId());
        assertNotNull(found);
        assertEquals(date, found.getDDate());
        assertEquals(dateTime, found.getDDateTime());
        assertEquals(instant, found.getDInstant());
        // OffsetDateTime：JDBC 按时间点存储，offset 可能被规整，比较时间点
        assertEquals(offset.toInstant(), found.getDOffset().toInstant());
    }

    @Test
    public void nullTemporalColumnsRoundTrip() {
        TemporalEntity entity = new TemporalEntity();
        jdbcDAO.save(entity);

        TemporalEntity found = jdbcDAO.findById(TemporalEntity.class, entity.getId());
        assertNotNull(found);
        assertEquals(null, found.getDDate());
        assertEquals(null, found.getDInstant());
    }

    @Test
    public void whereClauseWithLocalDateTimeParam() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 8, 26, 15, 30, 0); // 与 javaTimeRoundTrip 区分，避免多条匹配
        TemporalEntity entity = new TemporalEntity();
        entity.setDDateTime(dateTime);
        jdbcDAO.save(entity);

        TemporalEntity found = jdbcDAO.findOne(TemporalEntity.class,
                " where d_date_time = :dt ", SqlParam.create("dt", dateTime));
        assertNotNull(found);
        assertEquals(dateTime, found.getDDateTime());
    }

    static class FixedIdGenerator implements IdGenerator<Long> {
        private long sequence = 1;

        @Override
        public synchronized Long nextId() {
            return sequence++;
        }

        @Override
        public Class<Long> idType() {
            return Long.class;
        }
    }
}
