package io.github.sombreknight.feather.datasource;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 一主多从路由数据源
 *
 * <p>按 {@link DataSourceHolder} 中的 Key 路由到主/从数据源；未设置 Key 或 Key 不匹配时
 * 走默认（主）数据源——保证事务开始、健康检查等无 Key 场景默认主库。</p>
 *
 * <p>注意：不继承 Spring 的 AbstractRoutingDataSource，避免其默认构造器实例化
 * JndiDataSourceLookup 带来的隐藏 spring-context 依赖，core 保持最小依赖。</p>
 *
 * @author sombreknight
 */
public class RoutingDataSource implements DataSource, Closeable {

    private final Map<Object, DataSource> targetDataSources;
    private final DataSource defaultTargetDataSource;

    public RoutingDataSource(Map<Object, DataSource> targetDataSources, DataSource defaultTargetDataSource) {
        if (targetDataSources == null || targetDataSources.isEmpty()) {
            throw new IllegalArgumentException("targetDataSources 不能为空");
        }
        this.targetDataSources = Collections.unmodifiableMap(targetDataSources);
        this.defaultTargetDataSource = defaultTargetDataSource;
    }

    /**
     * 按当前线程的数据源 Key 决定目标数据源；无 Key 或 Key 未命中时返回默认（主）数据源
     */
    public DataSource determineTargetDataSource() {
        Object key = DataSourceHolder.getDataSourceKey();
        if (key != null) {
            DataSource target = targetDataSources.get(key);
            if (target != null) {
                return target;
            }
        }
        if (defaultTargetDataSource != null) {
            return defaultTargetDataSource;
        }
        throw new IllegalStateException("无法确定目标数据源: lookupKey=" + key);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return determineTargetDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return determineTargetDataSource().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return determineTargetDataSource().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        determineTargetDataSource().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        determineTargetDataSource().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return determineTargetDataSource().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return defaultTargetDataSource != null
                ? defaultTargetDataSource.getParentLogger()
                : Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return determineTargetDataSource().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return determineTargetDataSource().isWrapperFor(iface);
    }

    /**
     * 关闭所有目标数据源（含主从）
     */
    @Override
    public void close() throws IOException {
        for (DataSource dataSource : targetDataSources.values()) {
            if (dataSource instanceof Closeable) {
                try {
                    ((Closeable) dataSource).close();
                } catch (IOException e) {
                    // 单个数据源关闭失败不阻断整体关闭
                }
            }
        }
    }
}
