package io.github.sombreknight.feather.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feather ORM 配置（前缀 feather）
 *
 * <pre>
 * feather:
 *   orm:
 *     datasource:
 *       primary:             # 默认集群（也可用 others.default 命名默认集群）
 *         url: jdbc:mysql://localhost:3306/demo
 *         username: root
 *         password: xxx
 *         replicas:          # 该集群的从库（可选，读写分离）
 *           - url: jdbc:mysql://slave1:3306/demo
 *             username: root
 *             password: xxx
 *       others:              # 其他集群：每个独立数据库一个 key（可选）
 *         order:
 *           url: jdbc:mysql://localhost:3306/order
 *           username: root
 *           password: xxx
 *           dialect: mysql   # 可选，集群级方言覆盖（缺省 auto 探测）
 *           hikari:          # 可选，集群级池参数覆盖（缺省继承全局 hikari）
 *             maximum-pool-size: 5
 *       hikari:              # 全局池参数（所有集群继承，集群级可覆盖）
 *         maximum-pool-size: 20
 *     dialect: auto           # auto(默认，自动探测) | mysql | postgresql | sqlserver | oracle | sqlite | h2 | dm | default
 *     worker-id: 1            # 可选，雪花算法 workerId
 * </pre>
 *
 * <p>默认集群确定规则（按序）：{@code others.default} → 顶层 {@code primary} → {@code others.primary}；
 * 都不满足且配置了 {@code others} 时启动失败；都不配置时回退 Spring Boot 默认数据源。</p>
 *
 * @author sombreknight
 */
@ConfigurationProperties(prefix = "feather")
public class FeatherProperties {

    private Orm orm = new Orm();

    public Orm getOrm() {
        return orm;
    }

    public void setOrm(Orm orm) {
        this.orm = orm;
    }

    // ==================== 数据源 ====================

    public static class Datasource {

        /**
         * 默认集群（兼容旧配置）；也可在 {@link #others} 中用 default/primary 命名默认集群
         */
        private ConnectionInfo primary = new ConnectionInfo();

        /**
         * 其他集群：key 为集群名（DAO 上 {@code @FeatherDataSource("name")} 引用），
         * value 为连接配置；集群级可选配 replicas / dialect / hikari
         */
        private Map<String, ConnectionInfo> others = new LinkedHashMap<>();

        /**
         * 旧版顶层从库配置：仅当默认集群来自顶层 {@link #primary} 且其未配置 replicas 时生效（兼容）
         */
        private List<ConnectionInfo> replicas = new ArrayList<>();

        /**
         * 全局池参数（所有集群继承，集群级可覆盖单项）
         */
        private Hikari hikari = new Hikari();

        public ConnectionInfo getPrimary() {
            return primary;
        }

        public void setPrimary(ConnectionInfo primary) {
            this.primary = primary;
        }

        public Map<String, ConnectionInfo> getOthers() {
            return others;
        }

        public void setOthers(Map<String, ConnectionInfo> others) {
            this.others = others;
        }

        public List<ConnectionInfo> getReplicas() {
            return replicas;
        }

        public void setReplicas(List<ConnectionInfo> replicas) {
            this.replicas = replicas;
        }

        public Hikari getHikari() {
            return hikari;
        }

        public void setHikari(Hikari hikari) {
            this.hikari = hikari;
        }
    }

    public static class ConnectionInfo {

        /**
         * JDBC 连接 URL（必填），如 jdbc:mysql://localhost:3306/demo
         */
        private String url;

        /**
         * 数据库用户名（不继承、不默认，未配置即无账号）
         */
        private String username;

        /**
         * 数据库密码（不继承、不默认，未配置即无账号）
         */
        private String password;

        /**
         * JDBC 驱动类名（可选；缺省按 JDBC URL 自动推导）
         */
        private String driverClassName;

        /**
         * 集群级 SQL 方言覆盖（可选；缺省 auto 自动探测）
         */
        private String dialect;

        /**
         * 该集群的从库（可选，读写分离）
         */
        private List<ConnectionInfo> replicas = new ArrayList<>();

        /**
         * 集群级池参数覆盖（可选；缺省继承全局 hikari）
         */
        private Hikari hikari;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getDialect() {
            return dialect;
        }

        public void setDialect(String dialect) {
            this.dialect = dialect;
        }

        public List<ConnectionInfo> getReplicas() {
            return replicas;
        }

        public void setReplicas(List<ConnectionInfo> replicas) {
            this.replicas = replicas;
        }

        public Hikari getHikari() {
            return hikari;
        }

        public void setHikari(Hikari hikari) {
            this.hikari = hikari;
        }
    }

    /**
     * Hikari 池参数（可选，不配即用 Hikari 默认值）
     */
    public static class Hikari {

        /**
         * 连接池名称（默认 feather-&lt;集群名&gt;）
         */
        private String poolName;

        /**
         * 最大连接数
         */
        private Integer maximumPoolSize;

        /**
         * 最小空闲连接数
         */
        private Integer minimumIdle;

        /**
         * 连接超时（毫秒）
         */
        private Long connectionTimeout;

        /**
         * 空闲连接回收超时（毫秒）
         */
        private Long idleTimeout;

        /**
         * 连接最大存活时间（毫秒）
         */
        private Long maxLifetime;

        /**
         * 连接校验超时（毫秒）
         */
        private Long validationTimeout;

        /**
         * 连接泄漏检测阈值（毫秒）
         */
        private Long leakDetectionThreshold;

        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }

        public Integer getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(Integer maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public Integer getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(Integer minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public Long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public Long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(Long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public Long getValidationTimeout() {
            return validationTimeout;
        }

        public void setValidationTimeout(Long validationTimeout) {
            this.validationTimeout = validationTimeout;
        }

        public Long getLeakDetectionThreshold() {
            return leakDetectionThreshold;
        }

        public void setLeakDetectionThreshold(Long leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
        }
    }

    public static class Orm {

        /**
         * 是否启用 feather-orm，默认启用。置 false 时整个 ORM 不装配
         * （不建数据源 / JdbcDAO / 方言，也不因缺数据源而报错），用于脚手架按需开关。
         */
        private boolean enabled = true;

        /**
         * 数据源配置（默认集群 + 其他集群 + 全局池参数），ORM 的连接管理设施
         */
        private Datasource datasource = new Datasource();

        /**
         * 雪花算法 workerId（可选；多实例部署时建议显式配置避免重复）
         */
        private Long workerId;

        /**
         * SQL 方言：auto（默认，自动探测）| mysql | postgresql | sqlserver | oracle | sqlite | h2 | dm | default
         * 显式配置时作为所有未单独指定方言的集群的方言
         */
        private String dialect = "auto";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Datasource getDatasource() {
            return datasource;
        }

        public void setDatasource(Datasource datasource) {
            this.datasource = datasource;
        }

        public Long getWorkerId() {
            return workerId;
        }

        public void setWorkerId(Long workerId) {
            this.workerId = workerId;
        }

        public String getDialect() {
            return dialect;
        }

        public void setDialect(String dialect) {
            this.dialect = dialect;
        }
    }
}
