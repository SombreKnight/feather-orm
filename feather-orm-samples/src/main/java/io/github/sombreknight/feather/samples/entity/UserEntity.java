package io.github.sombreknight.feather.samples.entity;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;

import java.util.List;

/**
 * 用户实体
 *
 * <p>字段与列的约定映射：{@code userName} → {@code user_name}；复杂对象 / 集合自动 JSON 列。</p>
 *
 * @author sombreknight
 */
@Table("tb_user")
public class UserEntity extends BaseEntity<Long> {

    /** 约定映射 user_name */
    private String userName;

    private Integer age;

    /** 业务码枚举 → status 列 */
    private OrderStatus status;

    /** 复杂对象 → ext_info 列（JSON） */
    private ExtInfo extInfo;

    /** 泛型集合 → tags 列（JSON） */
    private List<String> tags;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public ExtInfo getExtInfo() {
        return extInfo;
    }

    public void setExtInfo(ExtInfo extInfo) {
        this.extInfo = extInfo;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
