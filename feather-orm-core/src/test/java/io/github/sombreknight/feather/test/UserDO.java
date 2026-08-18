package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.annotation.Column;
import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseDO;

import java.util.List;

/**
 * 测试实体：覆盖约定映射、@Column 覆盖、枚举（name 与业务码）、JSON 对象、JSON 泛型集合
 *
 * @author sombreknight
 */
@Table("tb_user")
public class UserDO extends BaseDO {

    /** 约定映射 user_name */
    private String userName;

    private Integer age;

    /** 业务码枚举 → status 列（int） */
    private OrderStatus status;

    /** 普通枚举 → type 列（varchar，存 name） */
    private TypeEnum type;

    /** 复杂对象 → ext_info 列（JSON） */
    private ExtInfo extInfo;

    /** 泛型集合 → tags 列（JSON） */
    private List<String> tags;

    /** @Column 显式指定列名（不符合约定时） */
    @Column("phone_no")
    private String phone;

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

    public TypeEnum getType() {
        return type;
    }

    public void setType(TypeEnum type) {
        this.type = type;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
