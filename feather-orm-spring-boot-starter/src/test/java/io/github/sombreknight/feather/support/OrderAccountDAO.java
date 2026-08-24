package io.github.sombreknight.feather.support;

import io.github.sombreknight.feather.annotation.FeatherDataSource;
import io.github.sombreknight.feather.core.BaseDAO;
import io.github.sombreknight.feather.test.AccountEntity;
import org.springframework.stereotype.Repository;

/**
 * 绑定 order 集群的测试 DAO（多数据源集成测试用）
 *
 * @author sombreknight
 */
@Repository
@FeatherDataSource("order")
public class OrderAccountDAO extends BaseDAO<AccountEntity> {
}
