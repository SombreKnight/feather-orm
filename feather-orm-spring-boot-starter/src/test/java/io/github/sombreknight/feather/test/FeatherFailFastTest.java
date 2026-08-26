package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.support.FailFastDAO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多数据源 fail-fast 校验：@FeatherDataSource 指向未配置的集群时启动失败
 *
 * @author sombreknight
 */
public class FeatherFailFastTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(FailFastDAO.class)
    static class FailFastConfig {
    }

    @Test
    public void contextFailsWhenClusterNotConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(FailFastConfig.class)
                .withPropertyValues(
                        "feather.orm.datasource.primary.url=jdbc:h2:mem:feather-ff;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "feather.orm.datasource.primary.username=sa",
                        "feather.orm.datasource.primary.password=",
                        "feather.orm.datasource.others.order.url=jdbc:h2:mem:feather-ff-order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "feather.orm.datasource.others.order.username=sa",
                        "feather.orm.datasource.others.order.password=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).hasMessageContaining("数据源集群未配置");
                    assertThat(failure).hasMessageContaining("not-exist-cluster");
                });
    }
}
