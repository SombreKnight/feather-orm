package io.github.sombreknight.feather.support;

import io.github.sombreknight.feather.annotation.FeatherDataSource;
import io.github.sombreknight.feather.core.BaseDAO;
import io.github.sombreknight.feather.test.AccountEntity;
import org.springframework.stereotype.Repository;

/**
 * 绑定 log 集群（PostgreSQL）的测试 DAO（混合引擎集成测试用）
 *
 * @author sombreknight
 */
@Repository
@FeatherDataSource("log")
public class LogAccountDAO extends BaseDAO<AccountEntity> {
}
