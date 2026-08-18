package io.github.sombreknight.feather.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源：按 {@link DataSourceHolder} 中的 Key 路由到主/从数据源
 *
 * @author sombreknight
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceHolder.getDataSourceKey();
    }
}
