package io.github.sombreknight.feather.support;

import io.github.sombreknight.feather.annotation.FeatherDataSource;
import io.github.sombreknight.feather.core.BaseDAO;
import io.github.sombreknight.feather.test.AccountEntity;
import org.springframework.stereotype.Repository;

/**
 * 用默认集群别名 primary 绑定的测试 DAO（验证 default/primary 回退逻辑）
 *
 * @author sombreknight
 */
@Repository
@FeatherDataSource("primary")
public class PrimaryAliasAccountDAO extends BaseDAO<AccountEntity> {
}
