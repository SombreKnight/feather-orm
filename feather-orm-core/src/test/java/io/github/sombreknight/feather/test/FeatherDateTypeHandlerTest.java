package io.github.sombreknight.feather.test;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.SqlParam;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.FeatherDateTypeHandler;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import io.github.sombreknight.feather.util.FeatherDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FeatherDate 类型处理器测试：DB 往返 + 零时间转换 + SqlParam 转换
 *
 * @author sombreknight
 */
public class FeatherDateTypeHandlerTest {

    private static final long MILLIS = 1700000000000L;

    private static HikariDataSource dataSource;
    private static JdbcDAO jdbcDAO;
    private static FeatherDateTypeHandler handler = new FeatherDateTypeHandler();

    @BeforeAll
    public static void init() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:feather-date;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        new JdbcTemplate(dataSource).execute("CREATE TABLE tb_event (" +
                "id BIGINT PRIMARY KEY," +
                "event_time TIMESTAMP" +
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
    public void dbRoundTrip() {
        EventEntity event = new EventEntity();
        event.setEventTime(new FeatherDate(MILLIS));
        jdbcDAO.save(event);

        EventEntity found = jdbcDAO.findById(EventEntity.class, event.getId());
        assertNotNull(found);
        assertEquals(new FeatherDate(MILLIS), found.getEventTime());
    }

    @Test
    public void nullRoundTrip() {
        EventEntity event = new EventEntity();
        event.setEventTime(null);
        jdbcDAO.save(event);

        EventEntity found = jdbcDAO.findById(EventEntity.class, event.getId());
        assertNull(found.getEventTime());
    }

    @Test
    public void zeroTimeJdbcValue() {
        Object value = handler.toJdbcValue(FeatherDate.ZERO_INST, null);
        assertEquals("0000-00-00 00:00:00", value);
    }

    @Test
    public void normalJdbcValue() {
        Object value = handler.toJdbcValue(new FeatherDate(MILLIS), null);
        assertInstanceOf(Timestamp.class, value);
        assertEquals(MILLIS, ((Timestamp) value).getTime());
    }

    @Test
    public void nullJdbcValue() {
        assertNull(handler.toJdbcValue(null, null));
    }

    @Test
    public void sqlParamConvertsFeatherDate() {
        Map<String, Object> normal = SqlParam.create("t", new FeatherDate(MILLIS)).toMap();
        assertInstanceOf(Timestamp.class, normal.get("t"));

        Map<String, Object> zero = SqlParam.create("t", FeatherDate.ZERO_INST).toMap();
        assertEquals("0000-00-00 00:00:00", zero.get("t"));
    }

    /**
     * 递增 id（独立于其它测试类）
     */
    static class FixedIdGenerator implements IdGenerator<Long> {
        private long next = 1;

        @Override
        public synchronized Long nextId() {
            return next++;
        }

        @Override
        public Class<Long> idType() {
            return Long.class;
        }
    }
}
