package io.github.sombreknight.feather.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.SnowflakeIdGenerator;
import io.github.sombreknight.feather.core.UuidIdGenerator;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.ReflectionRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.TypeHandler;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Feather ORM 自动配置
 *
 * <p>数据源 / NamedParameterJdbcTemplate / JdbcDAO / 事务管理器由
 * {@link FeatherDataSourceRegistrar} 按 {@code feather.datasource} 配置动态注册：
 * 单集群（兼容旧配置 {@code primary} + {@code replicas}）或多集群（新增 {@code others}）统一处理；
 * 未配置任何连接时优雅回退到 Spring Boot 默认数据源行为。</p>
 *
 * @author sombreknight
 */
@AutoConfiguration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(FeatherProperties.class)
@Import(FeatherDataSourceRegistrar.class)
@ConditionalOnClass({NamedParameterJdbcTemplate.class, HikariDataSource.class})
public class FeatherAutoConfiguration {

    // ==================== 类型处理器 ====================

    @Bean
    @ConditionalOnMissingBean(TypeHandlerRegistry.class)
    public TypeHandlerRegistry typeHandlerRegistry(ObjectProvider<List<TypeHandler>> userHandlers) {
        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        userHandlers.ifAvailable(handlers -> {
            if (handlers != null) {
                for (TypeHandler handler : handlers) {
                    registry.register(handler);
                }
            }
        });
        return registry;
    }

    // ==================== RowMapper ====================

    @Bean
    @ConditionalOnMissingBean(RowMapperSupport.class)
    public RowMapperSupport rowMapperSupport(TypeHandlerRegistry typeHandlerRegistry,
                                             FeatherProperties properties) {
        RowMapperFactory factory = "reflection".equalsIgnoreCase(properties.getOrm().getRowMapper())
                ? new ReflectionRowMapperFactory()
                : new JavassistRowMapperFactory();
        return new RowMapperSupport(typeHandlerRegistry, factory);
    }

    // ==================== ID 生成器 ====================

    /**
     * Long 主键默认生成器：雪花算法（workerId 可配，见 {@code feather.orm.worker-id}）
     */
    @Bean
    @ConditionalOnMissingBean(SnowflakeIdGenerator.class)
    public SnowflakeIdGenerator snowflakeIdGenerator(FeatherProperties properties) {
        Long workerId = properties.getOrm().getWorkerId();
        return workerId == null ? new SnowflakeIdGenerator() : new SnowflakeIdGenerator(workerId);
    }

    /**
     * String 主键默认生成器：UUID
     */
    @Bean
    @ConditionalOnMissingBean(UuidIdGenerator.class)
    public UuidIdGenerator uuidIdGenerator() {
        return new UuidIdGenerator();
    }

    /**
     * 全部 IdGenerator 的集合（供每个集群的 JdbcDAO 构造注入；用户自定义 IdGenerator Bean 自动并入）
     */
    @Bean
    public List<IdGenerator<?>> idGeneratorList(ObjectProvider<List<IdGenerator<?>>> idGenerators) {
        List<IdGenerator<?>> list = idGenerators.getIfAvailable();
        return list == null ? Collections.emptyList() : list;
    }
}
