package io.github.sombreknight.feather.support;

import io.github.sombreknight.feather.annotation.FeatherDataSource;
import io.github.sombreknight.feather.core.BaseDAO;
import io.github.sombreknight.feather.test.AccountEntity;
import org.springframework.stereotype.Repository;

/**
 * 指向不存在集群的 DAO（fail-fast 测试用，包路径置于 component scan 范围外）
 *
 * @author sombreknight
 */
@Repository
@FeatherDataSource("not-exist-cluster")
public class FailFastDAO extends BaseDAO<AccountEntity> {
}
