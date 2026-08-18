package io.github.sombreknight.feather.samples.web;

import io.github.sombreknight.feather.core.PagingResult;
import io.github.sombreknight.feather.samples.dao.UserDAO;
import io.github.sombreknight.feather.samples.domain.UserDO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户接口：演示 Feather ORM 全套 CRUD
 *
 * @author sombreknight
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Resource
    private UserDAO userDAO;

    /**
     * 新增
     */
    @PostMapping
    public UserDO create(@RequestBody UserDO user) {
        userDAO.saveDomain(user);
        return user;
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public UserDO get(@PathVariable Long id) {
        return userDAO.findById(id);
    }

    /**
     * 列表（可选按姓名过滤）
     */
    @GetMapping
    public List<UserDO> list(@RequestParam(required = false) String userName) {
        if (StringUtils.hasText(userName)) {
            return userDAO.findList(userDAO.getQueryHelper().whereEqual("userName", userName).orderByAsc("id"));
        }
        return userDAO.findList(userDAO.getQueryHelper().orderByAsc("id"));
    }

    /**
     * 更新（仅非 null 字段）
     */
    @PutMapping("/{id}")
    public boolean update(@PathVariable Long id, @RequestBody UserDO user) {
        user.setId(id);
        return userDAO.updateDomain(user);
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        UserDO user = new UserDO();
        user.setId(id);
        return userDAO.deleteDomain(user);
    }

    /**
     * 分页
     */
    @GetMapping("/page")
    public PagingResult<UserDO> page(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return userDAO.findPageByPageNum(userDAO.getQueryHelper().limit(page, size));
    }
}
