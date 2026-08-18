package io.github.sombreknight.feather.datasource;

/**
 * 当前线程数据源选择
 *
 * <p>仅在一主多从（replicas 非空）场景生效；单节点场景 JdbcDAO 不会读写该状态，零路由开销。</p>
 *
 * @author sombreknight
 */
public class DataSourceHolder {

    private static final ThreadLocal<String> DATA_SOURCE_KEY = new ThreadLocal<>();

    private DataSourceHolder() {
    }

    public static void setDataSourceKey(String key) {
        DATA_SOURCE_KEY.set(key);
    }

    public static String getDataSourceKey() {
        return DATA_SOURCE_KEY.get();
    }

    public static void clearDataSource() {
        DATA_SOURCE_KEY.remove();
    }
}
