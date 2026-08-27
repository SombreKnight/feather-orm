package io.github.sombreknight.feather.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * feather.orm.enabled 开关：false 时整个 ORM 不装配（不建 DAO/数据源/方言，
 * 缺数据源也不报错），用于脚手架按需启用。
 */
public class FeatherEnabledSwitchTest {

    @Configuration
    @EnableAutoConfiguration
    static class EmptyConfig {
    }

    @Test
    public void disabledSkipsAllOrmBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(EmptyConfig.class)
                .withPropertyValues("feather.orm.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("jdbcDAO");
                    assertThat(context).doesNotHaveBean("featherDialect");
                    assertThat(context).doesNotHaveBean("featherDataSource");
                    assertThat(context).doesNotHaveBean("typeHandlerRegistry");
                });
    }

    @Test
    public void disabledWithoutDataSourceDoesNotFail() {
        // 脚手架关键场景：关闭 orm 且完全不配数据源，应用必须能启动
        new ApplicationContextRunner()
                .withUserConfiguration(EmptyConfig.class)
                .withPropertyValues("feather.orm.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    public void enabledByDefaultStillAssembles() {
        new ApplicationContextRunner()
                .withUserConfiguration(EmptyConfig.class)
                .withPropertyValues(
                        "feather.orm.datasource.primary.url=jdbc:h2:mem:feather-en;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "feather.orm.datasource.primary.username=sa",
                        "feather.orm.datasource.primary.password=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("jdbcDAO");
                    assertThat(context).hasBean("featherDataSource");
                    assertThat(context).hasBean("primaryDialect"); // cluster 路径方言名为 {cluster}Dialect
                    assertThat(context).hasBean("typeHandlerRegistry");
                });
    }

}
