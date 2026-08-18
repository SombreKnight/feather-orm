package io.github.sombreknight.feather.samples.dao;

import io.github.sombreknight.feather.core.BaseDAO;
import io.github.sombreknight.feather.samples.domain.UserDO;
import org.springframework.stereotype.Repository;

/**
 * 用户 DAO：继承 BaseDAO 即获得全套 CRUD
 *
 * @author sombreknight
 */
@Repository
public class UserDAO extends BaseDAO<UserDO> {
}
