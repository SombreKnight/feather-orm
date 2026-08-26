package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.dialect.MySqlDialect;
import io.github.sombreknight.feather.dialect.SqlDialect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 用户自定义 SqlDialect Bean 替换验证（旧版 @ConditionalOnMissingBean(SqlDialect.class) 语义）：
 * 用户注册的方言 Bean 优先于自动探测，所有集群 JdbcDAO 使用该方言
 *
 * @author sombreknight
 */
@SpringBootTest(classes = {StarterTestApplication.class, FeatherCustomDialectTest.CustomDialectConfig.class}, properties = {
        "feather.orm.datasource.primary.url=jdbc:h2:mem:feather-cd;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.orm.datasource.primary.username=sa",
        "feather.orm.datasource.primary.password=",
        "feather.orm.datasource.others.order.url=jdbc:h2:mem:feather-cd-order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "feather.orm.datasource.others.order.username=sa",
        "feather.orm.datasource.others.order.password="

})
public class FeatherCustomDialectTest {

    @Configuration
    static class CustomDialectConfig {
        @Bean
        SqlDialect customSqlDialect() {
            return new MySqlDialect();
        }
    }

    @Autowired
    private JdbcDAO jdbcDAO;

    @Autowired
    @Qualifier("orderJdbcDAO")
    private JdbcDAO orderJdbcDAO;

    @Autowired
    @Qualifier("customSqlDialect")
    private SqlDialect customSqlDialect;

    @Test
    public void customDialectBeanReplacesDetection() {
        // 默认集群与 order 集群均使用用户自定义方言实例（未做元数据探测）
        assertSame(customSqlDialect, jdbcDAO.getDialect());
        assertSame(customSqlDialect, orderJdbcDAO.getDialect());
    }
}
