package io.github.sombreknight.feather.core;

import java.io.Serializable;

/**
 * 实体基类
 *
 * <p>唯一强制约定：主键字段。继承即获得完整 CRUD 能力。</p>
 *
 * <pre>
 * &#064;Table("tb_user")
 * public class UserEntity extends BaseEntity {
 *     private String userName;   // 约定映射 user_name
 *     private OrderStatus status; // 枚举（CodeEnum）映射业务码
 *     private ExtInfo extInfo;    // 复杂对象自动 JSON 存储
 *     // getter / setter
 * }
 * </pre>
 *
 * @author sombreknight
 */
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（Long）
     *
     * <p>默认列名 id；如需其他列名（如 uid），通过 {@code @Table(idColumn = "uid")} 指定。</p>
     */
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
