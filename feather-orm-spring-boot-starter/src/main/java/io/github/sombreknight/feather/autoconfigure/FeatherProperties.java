package io.github.sombreknight.feather.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Feather ORM 配置（前缀 feather）
 *
 * <pre>
 * feather:
 *   datasource:
 *     primary:
 *       url: jdbc:mysql://localhost:3306/demo
 *       username: root
 *       password: xxx
 *     replicas:            # 可选，不配即单节点
 *       - url: jdbc:mysql://slave1:3306/demo
 *         username: root
 *         password: xxx
 *     hikari:              # 可选，主从共用，默认 Hikari 参数
 *       maximum-pool-size: 20
 *   orm:
 *     row-mapper: javassist   # javassist | reflection
 *     worker-id: 1            # 可选，雪花算法 workerId
 * </pre>
 *
 * @author sombreknight
 */
@ConfigurationProperties(prefix = "feather")
public class FeatherProperties {

    private Datasource datasource = new Datasource();
    private Orm orm = new Orm();

    public Datasource getDatasource() {
        return datasource;
    }

    public void setDatasource(Datasource datasource) {
        this.datasource = datasource;
    }

    public Orm getOrm() {
        return orm;
    }

    public void setOrm(Orm orm) {
        this.orm = orm;
    }

    // ==================== 数据源 ====================

    public static class Datasource {

        private ConnectionInfo primary = new ConnectionInfo();
        private List<ConnectionInfo> replicas = new ArrayList<>();
        private Hikari hikari = new Hikari();

        public ConnectionInfo getPrimary() {
            return primary;
        }

        public void setPrimary(ConnectionInfo primary) {
            this.primary = primary;
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

        private String url;
        private String username;
        private String password;
        private String driverClassName;

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
    }

    /**
     * Hikari 池参数（可选，不配即用 Hikari 默认值）
     */
    public static class Hikari {

        private String poolName;
        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private Long connectionTimeout;
        private Long idleTimeout;
        private Long maxLifetime;
        private Long validationTimeout;
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

    // ==================== ORM 行为 ====================

    public static class Orm {

        /**
         * RowMapper 实现：javassist（默认，字节码生成）| reflection（纯反射兜底）
         */
        private String rowMapper = "javassist";

        /**
         * 雪花算法 workerId（可选；多实例部署时建议显式配置避免重复）
         */
        private Long workerId;

        public String getRowMapper() {
            return rowMapper;
        }

        public void setRowMapper(String rowMapper) {
            this.rowMapper = rowMapper;
        }

        public Long getWorkerId() {
            return workerId;
        }

        public void setWorkerId(Long workerId) {
            this.workerId = workerId;
        }
    }
}
