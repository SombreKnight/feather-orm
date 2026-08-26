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
 * {@link FeatherDataSourceRegistrar} 按 {@code feather.orm.datasource} 配置动态注册：
 * 单集群（{@code primary} + {@code replicas}）或多集群（{@code others}）统一处理；
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

    /**
     * RowMapper 支持：默认 Javassist 字节码生成，环境不允许时自动降级纯反射（issue #7）。
     *
     * <p>强制指定（仅高级文档提及）：{@code -Dfeather.orm.row-mapper=reflection} 或
     * {@code -Dfeather.orm.row-mapper=javassist}（显式指定时不降级）。</p>
     */
    @Bean
    @ConditionalOnMissingBean(RowMapperSupport.class)
    public RowMapperSupport rowMapperSupport(TypeHandlerRegistry typeHandlerRegistry) {
        String forced = System.getProperty("feather.orm.row-mapper");
        if ("reflection".equalsIgnoreCase(forced)) {
            return new RowMapperSupport(typeHandlerRegistry, new ReflectionRowMapperFactory());
        }
        if ("javassist".equalsIgnoreCase(forced)) {
            return new RowMapperSupport(typeHandlerRegistry, new JavassistRowMapperFactory());
        }
        if (javassistAvailable()) {
            // 自动模式：Javassist 优先，运行期字节码生成失败时实例级降级为反射
            return new RowMapperSupport(typeHandlerRegistry,
                    new JavassistRowMapperFactory(), new ReflectionRowMapperFactory());
        }
        return new RowMapperSupport(typeHandlerRegistry, new ReflectionRowMapperFactory());
    }

    /**
     * 探测当前环境是否支持 Javassist 字节码生成（安全策略 / GraalVM 等禁用时失败）
     */
    private static boolean javassistAvailable() {
        try {
            javassist.ClassPool pool = new javassist.ClassPool();
            pool.appendSystemPath();
            javassist.CtClass probe = pool.makeClass(
                    "io.github.sombreknight.feather.autoconfigure.FeatherRowMapperProbe");
            probe.setModifiers(java.lang.reflect.Modifier.PUBLIC);
            probe.getClassFile2().setMajorVersion(52);
            probe.toClass(FeatherAutoConfiguration.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
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
