package io.github.sombreknight.feather.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import io.github.sombreknight.feather.core.JdbcDAO;
import io.github.sombreknight.feather.datasource.DataSourceKey;
import io.github.sombreknight.feather.datasource.RoutingDataSource;
import io.github.sombreknight.feather.dialect.DialectRegistry;
import io.github.sombreknight.feather.dialect.SqlDialect;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多数据源 Bean 注册器
 *
 * <p>按 {@code feather.orm.datasource} 配置为每个集群注册一组 Bean：</p>
 * <ul>
 *   <li>DataSource（有从库则为 RoutingDataSource）— {@code <name>DataSource}</li>
 *   <li>NamedParameterJdbcTemplate — {@code <name>NamedParameterJdbcTemplate}</li>
 *   <li>JdbcDAO（各自独立方言 / 从库数量）— {@code <name>JdbcDAO}</li>
 *   <li>DataSourceTransactionManager — {@code <name>TransactionManager}</li>
 *   <li>TransactionTemplate — {@code <name>TransactionTemplate}</li>
 * </ul>
 *
 * <p>默认集群额外以无前缀主 Bean 名（{@code featherDataSource} / {@code jdbcDAO} / {@code transactionManager}
 * / {@code transactionTemplate} / {@code namedParameterJdbcTemplate}）注册并标记 {@code @Primary}，
 * 保证旧代码 {@code @Autowired} 行为不变；同时注册 {@code <name>} 别名，使 {@code Map&lt;String, JdbcDAO&gt;}
 * 可按集群名取用。</p>
 *
 * <p>未配置任何 {@code feather.orm.datasource} 连接时（primary 为空且 others 为空）不注册任何 Bean，
 * 优雅回退 Spring Boot 默认数据源。</p>
 *
 * @author sombreknight
 */
public class FeatherDataSourceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry,
                                        org.springframework.beans.factory.support.BeanNameGenerator importBeanNameGenerator) {
        FeatherProperties props = Binder.get(environment).bind("feather", FeatherProperties.class)
                .orElseGet(FeatherProperties::new);
        FeatherProperties.Datasource dsCfg = props.getOrm().getDatasource();

        // 开关关闭（feather.orm.enabled=false）：彻底退出装配，不注册任何 bean
        // （AutoConfiguration 条件已拦截主路径；此处双保险覆盖直接 @Import Registrar 的场景）
        if (!props.getOrm().isEnabled()) {
            return;
        }

        // 用户自定义 SqlDialect Bean（等价旧版 @ConditionalOnMissingBean 语义），需在注册任何方言 Bean 之前探测
        String userDialectBean = findUserDialectBean(registry);
        // 用户已自行配置 DataSource（此时自动配置尚未处理，探测到的均为用户 Bean）：不接管数据源
        String userDataSourceBean = findDataSourceBean(registry);
        if (userDataSourceBean != null) {
            registerFallbackJdbcDAO(registry, props, userDialectBean, userDataSourceBean);
            return;
        }

        List<Cluster> clusters = resolveClusters(dsCfg);
        if (clusters.isEmpty()) {
            // 未配置 feather.orm.datasource：回退 Spring Boot 默认数据源，但仍注册默认 JdbcDAO / TransactionTemplate（兼容旧版）
            registerFallbackJdbcDAO(registry, props, userDialectBean, null);
            return;
        }
        for (Cluster cluster : clusters) {
            registerCluster(registry, cluster, props, userDialectBean);
        }
    }

    private String findDataSourceBean(BeanDefinitionRegistry registry) {
        if (registry instanceof DefaultListableBeanFactory factory) {
            String[] dataSources = factory.getBeanNamesForType(DataSource.class);
            return dataSources.length > 0 ? dataSources[0] : null;
        }
        return null;
    }

    /**
     * 回退场景（用户自配 DataSource / 未配置 feather 数据源）：注册默认 JdbcDAO 与 TransactionTemplate，
     * 复用 Spring Boot 默认的 NamedParameterJdbcTemplate 与 transactionManager，行为与旧版一致。
     */
    private void registerFallbackJdbcDAO(BeanDefinitionRegistry registry, FeatherProperties props,
                                         String userDialectBean, String userDataSourceBean) {
        String dialectBeanName;
        if (userDialectBean != null) {
            dialectBeanName = userDialectBean;
        } else {
            String globalDialect = props.getOrm().getDialect();
            if (StringUtils.hasText(globalDialect) && !"auto".equalsIgnoreCase(globalDialect)) {
                dialectBeanName = registerDialectByName(registry, "featherDialect", globalDialect);
            } else if (userDataSourceBean != null) {
                dialectBeanName = registerDialectByDetect(registry, "featherDialect", userDataSourceBean);
            } else {
                // Boot 数据源稍后由自动配置注册（默认名 dataSource），运行时解析
                dialectBeanName = registerDialectByDetect(registry, "featherDialect", "dataSource");
            }
        }
        registerJdbcDAO(registry, "jdbcDAO", "namedParameterJdbcTemplate", dialectBeanName, 0);
        // TransactionTemplate 依赖 Boot 自动配置的 transactionManager（默认名），与旧版 @ConditionalOnMissingBean 语义一致
        RootBeanDefinition txTemplateBd = new RootBeanDefinition(TransactionTemplate.class);
        txTemplateBd.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference("transactionManager"));
        registry.registerBeanDefinition("transactionTemplate", txTemplateBd);
    }

    private String findUserDialectBean(BeanDefinitionRegistry registry) {
        if (registry instanceof DefaultListableBeanFactory factory) {
            String[] userDialects = factory.getBeanNamesForType(SqlDialect.class);
            return userDialects.length > 0 ? userDialects[0] : null;
        }
        return null;
    }

    // ==================== 集群解析 ====================

    private static class Cluster {
        final String name;
        final boolean defaultCluster;
        final FeatherProperties.ConnectionInfo info;
        final List<FeatherProperties.ConnectionInfo> replicas;

        Cluster(String name, boolean defaultCluster, FeatherProperties.ConnectionInfo info,
                List<FeatherProperties.ConnectionInfo> replicas) {
            this.name = name;
            this.defaultCluster = defaultCluster;
            this.info = info;
            this.replicas = replicas == null ? new ArrayList<>() : replicas;
        }
    }

    /**
     * 解析集群列表：默认集群（others.default → 顶层 primary → others.primary）+ 其余 others
     */
    private List<Cluster> resolveClusters(FeatherProperties.Datasource dsCfg) {
        Map<String, FeatherProperties.ConnectionInfo> others = dsCfg.getOthers() == null
                ? new java.util.LinkedHashMap<>() : dsCfg.getOthers();

        Cluster defaultCluster = null;
        if (others.containsKey("default") && hasUrl(others.get("default"))) {
            defaultCluster = new Cluster("default", true, others.get("default"), others.get("default").getReplicas());
        } else if (dsCfg.getPrimary() != null && hasUrl(dsCfg.getPrimary())) {
            List<FeatherProperties.ConnectionInfo> replicas = dsCfg.getPrimary().getReplicas();
            if (replicas == null || replicas.isEmpty()) {
                replicas = dsCfg.getReplicas(); // 兼容旧版顶层 replicas 配置
            }
            defaultCluster = new Cluster("primary", true, dsCfg.getPrimary(), replicas);
        } else if (others.containsKey("primary") && hasUrl(others.get("primary"))) {
            defaultCluster = new Cluster("primary", true, others.get("primary"), others.get("primary").getReplicas());
        }

        List<Cluster> clusters = new ArrayList<>();
        if (defaultCluster != null) {
            clusters.add(defaultCluster);
        } else if (!others.isEmpty()) {
            throw new IllegalStateException("feather.orm.datasource 配置了 others 但未指定默认集群，"
                    + "请配置 feather.orm.datasource.primary 或在 others 中配置 default/primary 集群");
        }

        for (Map.Entry<String, FeatherProperties.ConnectionInfo> entry : others.entrySet()) {
            if ("default".equals(entry.getKey()) || "primary".equals(entry.getKey())) {
                continue; // 已作为默认集群处理
            }
            if (entry.getValue() == null) {
                continue;
            }
            clusters.add(new Cluster(entry.getKey(), false, entry.getValue(), entry.getValue().getReplicas()));
        }
        return clusters;
    }

    private static boolean hasUrl(FeatherProperties.ConnectionInfo info) {
        return info != null && StringUtils.hasText(info.getUrl());
    }

    // ==================== 集群 Bean 注册 ====================

    private void registerCluster(BeanDefinitionRegistry registry, Cluster cluster, FeatherProperties props,
                                 String userDialectBean) {
        String name = cluster.name;
        boolean isDefault = cluster.defaultCluster;

        // Bean 名：默认集群用无前缀主名（兼容旧代码），其余用 <name> 前缀；另注册 <name> 别名
        String dsName = isDefault ? "featherDataSource" : name + "DataSource";
        String templateName = isDefault ? "namedParameterJdbcTemplate" : name + "NamedParameterJdbcTemplate";
        String tmName = isDefault ? "transactionManager" : name + "TransactionManager";
        String txTemplateName = isDefault ? "transactionTemplate" : name + "TransactionTemplate";
        String jdbcDAOName = isDefault ? "jdbcDAO" : name + "JdbcDAO";

        // 1. DataSource（有从库则主库 + 从库 + RoutingDataSource）
        registerDataSource(registry, cluster, props, dsName);

        // 2. NamedParameterJdbcTemplate
        registerTemplate(registry, templateName, dsName);

        // 3. 方言（各集群独立；集群显式配置 > 用户自定义 SqlDialect Bean > 全局 orm.dialect 显式 > 自动探测）
        String dialectBeanName = registerDialect(registry, cluster, props, dsName, userDialectBean);

        // 4. JdbcDAO（引用全局 idGeneratorList / rowMapperSupport）
        registerJdbcDAO(registry, jdbcDAOName, templateName, dialectBeanName, cluster.replicas.size());

        // 5. 事务：每集群独立 DataSourceTransactionManager + TransactionTemplate
        registerTransaction(registry, tmName, txTemplateName, dsName);

        // 默认集群：@Primary + <name> 别名
        if (isDefault) {
            markPrimary(registry, dsName, templateName, tmName, txTemplateName, jdbcDAOName);
            registerAlias(registry, dsName, name + "DataSource");
            registerAlias(registry, templateName, name + "NamedParameterJdbcTemplate");
            registerAlias(registry, tmName, name + "TransactionManager");
            registerAlias(registry, txTemplateName, name + "TransactionTemplate");
            registerAlias(registry, jdbcDAOName, name + "JdbcDAO");
        }
    }

    private void registerDataSource(BeanDefinitionRegistry registry, Cluster cluster,
                                    FeatherProperties props, String dsName) {
        FeatherProperties.Hikari globalHikari = props.getOrm().getDatasource().getHikari();
        List<FeatherProperties.ConnectionInfo> replicas = cluster.replicas;

        if (replicas.isEmpty()) {
            // 单节点集群：<name>DataSource 即主库 Hikari，零路由开销
            RootBeanDefinition masterBd = buildHikariBean(cluster.info, globalHikari, "feather-" + cluster.name);
            registry.registerBeanDefinition(dsName, masterBd);
            return;
        }
        // 一主多从：主库/从库各自命名 Bean + 路由数据源
        String masterName = cluster.name + "MasterDataSource";
        RootBeanDefinition masterBd = buildHikariBean(cluster.info, globalHikari, "feather-" + cluster.name);
        registry.registerBeanDefinition(masterName, masterBd);

        ManagedMap<Object, Object> targets = new ManagedMap<>();
        targets.put(DataSourceKey.MASTER, new RuntimeBeanReference(masterName));
        for (int i = 0; i < replicas.size(); i++) {
            String replicaName = cluster.name + "Replica" + (i + 1) + "DataSource";
            RootBeanDefinition replicaBd = buildHikariBean(replicas.get(i), globalHikari,
                    "feather-" + cluster.name + "-r" + (i + 1));
            registry.registerBeanDefinition(replicaName, replicaBd);
            targets.put(DataSourceKey.SLAVE_PREFIX + (i + 1), new RuntimeBeanReference(replicaName));
        }
        // RoutingDataSource：构造 (Map<Object,DataSource>, DataSource default)
        RootBeanDefinition routingBd = new RootBeanDefinition(RoutingDataSource.class);
        ConstructorArgumentValues ctor = new ConstructorArgumentValues();
        ctor.addIndexedArgumentValue(0, targets);
        ctor.addIndexedArgumentValue(1, new RuntimeBeanReference(masterName));
        routingBd.setConstructorArgumentValues(ctor);
        registry.registerBeanDefinition(dsName, routingBd);
    }

    private RootBeanDefinition buildHikariBean(FeatherProperties.ConnectionInfo info,
                                               FeatherProperties.Hikari globalHikari,
                                               String poolName) {
        if (!hasUrl(info)) {
            throw new IllegalStateException("feather.orm.datasource 集群缺少 url 配置: " + info);
        }
        RootBeanDefinition bd = new RootBeanDefinition(HikariDataSource.class);
        bd.setPropertyValues(buildHikariProperties(info, globalHikari, poolName));
        return bd;
    }

    private org.springframework.beans.MutablePropertyValues buildHikariProperties(
            FeatherProperties.ConnectionInfo info, FeatherProperties.Hikari globalHikari, String poolName) {
        FeatherProperties.Hikari clusterHikari = info.getHikari();
        org.springframework.beans.MutablePropertyValues pv = new org.springframework.beans.MutablePropertyValues();
        pv.add("jdbcUrl", info.getUrl());
        if (info.getUsername() != null) {
            pv.add("username", info.getUsername());
        }
        if (info.getPassword() != null) {
            pv.add("password", info.getPassword());
        }
        if (StringUtils.hasText(info.getDriverClassName())) {
            pv.add("driverClassName", info.getDriverClassName());
        }
        String poolNameValue = StringUtils.hasText(clusterHikari != null ? clusterHikari.getPoolName() : null)
                ? clusterHikari.getPoolName() : poolName;
        pv.add("poolName", poolNameValue);
        addIfPresent(pv, "maximumPoolSize", pick(clusterHikari, globalHikari,
                h -> h.getMaximumPoolSize(), globalHikari.getMaximumPoolSize()));
        addIfPresent(pv, "minimumIdle", pick(clusterHikari, globalHikari,
                h -> h.getMinimumIdle(), globalHikari.getMinimumIdle()));
        addIfPresent(pv, "connectionTimeout", pick(clusterHikari, globalHikari,
                h -> h.getConnectionTimeout(), globalHikari.getConnectionTimeout()));
        addIfPresent(pv, "idleTimeout", pick(clusterHikari, globalHikari,
                h -> h.getIdleTimeout(), globalHikari.getIdleTimeout()));
        addIfPresent(pv, "maxLifetime", pick(clusterHikari, globalHikari,
                h -> h.getMaxLifetime(), globalHikari.getMaxLifetime()));
        addIfPresent(pv, "validationTimeout", pick(clusterHikari, globalHikari,
                h -> h.getValidationTimeout(), globalHikari.getValidationTimeout()));
        addIfPresent(pv, "leakDetectionThreshold", pick(clusterHikari, globalHikari,
                h -> h.getLeakDetectionThreshold(), globalHikari.getLeakDetectionThreshold()));
        return pv;
    }

    @FunctionalInterface
    private interface HikariGetter<T> {
        T get(FeatherProperties.Hikari hikari);
    }

    private static <T> T pick(FeatherProperties.Hikari clusterHikari, FeatherProperties.Hikari globalHikari,
                              HikariGetter<T> getter, T globalDefault) {
        if (clusterHikari != null) {
            T v = getter.get(clusterHikari);
            if (v != null) {
                return v;
            }
        }
        if (globalHikari != null) {
            T v = getter.get(globalHikari);
            if (v != null) {
                return v;
            }
        }
        return globalDefault;
    }

    private static void addIfPresent(org.springframework.beans.MutablePropertyValues pv, String name, Object value) {
        if (value != null) {
            pv.add(name, value);
        }
    }

    private void registerTemplate(BeanDefinitionRegistry registry, String templateName, String dsName) {
        RootBeanDefinition bd = new RootBeanDefinition(NamedParameterJdbcTemplate.class);
        bd.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference(dsName));
        registry.registerBeanDefinition(templateName, bd);
    }

    /**
     * 集群方言：集群显式配置 &gt; 用户自定义 SqlDialect Bean &gt; 全局 orm.dialect 显式 &gt; 自动探测
     */
    private String registerDialect(BeanDefinitionRegistry registry, Cluster cluster, FeatherProperties props,
                                   String dsName, String userDialectBean) {
        String clusterDialect = cluster.info.getDialect();
        if (StringUtils.hasText(clusterDialect)) {
            return registerDialectByName(registry, cluster.name + "Dialect", clusterDialect);
        }
        if (userDialectBean != null) {
            return userDialectBean;
        }
        String globalDialect = props.getOrm().getDialect();
        if (StringUtils.hasText(globalDialect) && !"auto".equalsIgnoreCase(globalDialect)) {
            return registerDialectByName(registry, cluster.name + "Dialect", globalDialect);
        }
        // 自动探测：默认集群探测其数据源，其余集群探测各自数据源
        return registerDialectByDetect(registry, cluster.name + "Dialect", dsName);
    }

    private String registerDialectByName(BeanDefinitionRegistry registry, String beanName, String alias) {
        RootBeanDefinition bd = new RootBeanDefinition(DialectRegistry.class);
        bd.setFactoryMethodName("byName");
        bd.getConstructorArgumentValues().addIndexedArgumentValue(0, alias);
        registry.registerBeanDefinition(beanName, bd);
        return beanName;
    }

    private String registerDialectByDetect(BeanDefinitionRegistry registry, String beanName, String dsName) {
        RootBeanDefinition bd = new RootBeanDefinition(DialectRegistry.class);
        bd.setFactoryMethodName("detect");
        bd.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference(dsName));
        registry.registerBeanDefinition(beanName, bd);
        return beanName;
    }

    private void registerJdbcDAO(BeanDefinitionRegistry registry, String jdbcDAOName, String templateName,
                                 String dialectBeanName, int slaveCount) {
        RootBeanDefinition bd = new RootBeanDefinition(JdbcDAO.class);
        ConstructorArgumentValues ctor = new ConstructorArgumentValues();
        ctor.addIndexedArgumentValue(0, new RuntimeBeanReference(templateName));
        ctor.addIndexedArgumentValue(1, new RuntimeBeanReference("idGeneratorList"));
        ctor.addIndexedArgumentValue(2, new RuntimeBeanReference("rowMapperSupport"));
        ctor.addIndexedArgumentValue(3, slaveCount);
        ctor.addIndexedArgumentValue(4, new RuntimeBeanReference(dialectBeanName));
        bd.setConstructorArgumentValues(ctor);
        registry.registerBeanDefinition(jdbcDAOName, bd);
    }

    private void registerTransaction(BeanDefinitionRegistry registry, String tmName, String txTemplateName,
                                     String dsName) {
        RootBeanDefinition tmBd = new RootBeanDefinition(DataSourceTransactionManager.class);
        tmBd.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference(dsName));
        registry.registerBeanDefinition(tmName, tmBd);

        RootBeanDefinition txTemplateBd = new RootBeanDefinition(TransactionTemplate.class);
        txTemplateBd.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference(tmName));
        registry.registerBeanDefinition(txTemplateName, txTemplateBd);
    }

    private void markPrimary(BeanDefinitionRegistry registry, String... beanNames) {
        for (String beanName : beanNames) {
            BeanDefinition bd = registry.getBeanDefinition(beanName);
            if (bd instanceof AbstractBeanDefinition abd) {
                abd.setPrimary(true);
            }
        }
    }

    private void registerAlias(BeanDefinitionRegistry registry, String beanName, String alias) {
        try {
            registry.registerAlias(beanName, alias);
        } catch (Exception e) {
            // 别名已存在时忽略（如用户自定义同名 Bean）
        }
    }
}
