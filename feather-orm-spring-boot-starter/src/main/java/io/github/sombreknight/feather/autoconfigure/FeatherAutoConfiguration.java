package io.github.sombreknight.feather.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.IdGenerator;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.core.SnowflakeIdGenerator;
import io.github.sombreknight.feather.datasource.DataSourceKey;
import io.github.sombreknight.feather.datasource.RoutingDataSource;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.SqlDialect;
import io.github.sombreknight.feather.mapping.JavassistRowMapperFactory;
import io.github.sombreknight.feather.mapping.Mapper;
import io.github.sombreknight.feather.mapping.ReflectionRowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperFactory;
import io.github.sombreknight.feather.mapping.RowMapperSupport;
import io.github.sombreknight.feather.type.TypeHandler;
import io.github.sombreknight.feather.type.TypeHandlerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feather ORM 自动配置
 *
 * <p>当配置了 {@code feather.datasource.primary.url} 时，本配置在 Spring Boot 默认数据源
 * 自动配置之前创建 DataSource / NamedParameterJdbcTemplate / 事务管理器，接管持久层配置；
 * 未配置时优雅回退到 Boot 默认数据源行为。</p>
 *
 * @author sombreknight
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(FeatherProperties.class)
@ConditionalOnClass({NamedParameterJdbcTemplate.class, HikariDataSource.class})
public class FeatherAutoConfiguration {

    // ==================== 数据源（可选接管） ====================

    /**
     * 框架自有数据源：主库 + 可选从库（路由）
     */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "feather.datasource.primary", name = "url")
    public DataSource featherDataSource(FeatherProperties properties) {
        String url = properties.getDatasource().getPrimary().getUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException("feather.datasource.primary.url 未配置");
        }
        HikariDataSource primary = buildHikariDataSource(properties, properties.getDatasource().getPrimary(), "primary");

        List<FeatherProperties.ConnectionInfo> replicas = properties.getDatasource().getReplicas();
        if (replicas == null || replicas.isEmpty()) {
            return primary; // 单节点，零路由开销
        }
        Map<Object, DataSource> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceKey.MASTER, primary);
        for (int i = 0; i < replicas.size(); i++) {
            HikariDataSource replica = buildHikariDataSource(properties, replicas.get(i), "replica-" + (i + 1));
            targetDataSources.put(DataSourceKey.SLAVE_PREFIX + (i + 1), replica);
        }
        // 未显式指定数据源 Key（如事务开始、健康检查等场景）默认走主库
        return new RoutingDataSource(targetDataSources, primary);
    }

    private HikariDataSource buildHikariDataSource(FeatherProperties properties,
                                                   FeatherProperties.ConnectionInfo info,
                                                   String name) {
        if (info.getUrl() == null || info.getUrl().trim().isEmpty()) {
            throw new IllegalStateException("feather.datasource " + name + " 的 url 未配置");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(info.getUrl());
        dataSource.setUsername(info.getUsername());
        dataSource.setPassword(info.getPassword());
        if (info.getDriverClassName() != null && !info.getDriverClassName().trim().isEmpty()) {
            dataSource.setDriverClassName(info.getDriverClassName());
        }

        FeatherProperties.Hikari hikari = properties.getDatasource().getHikari();
        String poolName = hikari.getPoolName();
        dataSource.setPoolName((poolName == null || poolName.trim().isEmpty())
                ? "feather-" + name : poolName + "-" + name);
        if (hikari.getMaximumPoolSize() != null) {
            dataSource.setMaximumPoolSize(hikari.getMaximumPoolSize());
        }
        if (hikari.getMinimumIdle() != null) {
            dataSource.setMinimumIdle(hikari.getMinimumIdle());
        }
        if (hikari.getConnectionTimeout() != null) {
            dataSource.setConnectionTimeout(hikari.getConnectionTimeout());
        }
        if (hikari.getIdleTimeout() != null) {
            dataSource.setIdleTimeout(hikari.getIdleTimeout());
        }
        if (hikari.getMaxLifetime() != null) {
            dataSource.setMaxLifetime(hikari.getMaxLifetime());
        }
        if (hikari.getValidationTimeout() != null) {
            dataSource.setValidationTimeout(hikari.getValidationTimeout());
        }
        if (hikari.getLeakDetectionThreshold() != null) {
            dataSource.setLeakDetectionThreshold(hikari.getLeakDetectionThreshold());
        }
        return dataSource;
    }

    // ==================== SQL 方言 ====================

    /**
     * SQL 方言：auto 时从数据源元数据自动探测（MySQL/PostgreSQL/SQL Server/Oracle/SQLite/H2/达梦等），
     * 也可显式配置 feather.orm.dialect 覆盖；用户可自定义 SqlDialect Bean 替换。
     */
    @Bean
    @ConditionalOnMissingBean(SqlDialect.class)
    public SqlDialect sqlDialect(DataSource dataSource, FeatherProperties properties) {
        String cfg = properties.getOrm().getDialect();
        if (cfg == null || cfg.trim().isEmpty() || "auto".equalsIgnoreCase(cfg.trim())) {
            return DialectRegistry.detect(dataSource);
        }
        return DialectRegistry.byName(cfg.trim());
    }

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

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator idGenerator(FeatherProperties properties) {
        Long workerId = properties.getOrm().getWorkerId();
        return workerId == null ? new SnowflakeIdGenerator() : new SnowflakeIdGenerator(workerId);
    }

    // ==================== 核心 DAO ====================

    @Bean
    @ConditionalOnMissingBean(JdbcDAO.class)
    public JdbcDAO jdbcDAO(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                           IdGenerator idGenerator,
                           RowMapperSupport rowMapperSupport,
                           SqlDialect sqlDialect,
                           FeatherProperties properties) {
        // 全局 Mapper 与注入方言保持一致（实体映射的标识符引用按当前方言生成）
        Mapper.getInstance().setDialect(sqlDialect);
        List<FeatherProperties.ConnectionInfo> replicas = properties.getDatasource().getReplicas();
        int slaveCount = replicas == null ? 0 : replicas.size();
        return new JdbcDAO(namedParameterJdbcTemplate, idGenerator, rowMapperSupport, slaveCount, sqlDialect);
    }

    // ==================== 编程式事务 ====================

    @Bean
    @ConditionalOnMissingBean(TransactionTemplate.class)
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
