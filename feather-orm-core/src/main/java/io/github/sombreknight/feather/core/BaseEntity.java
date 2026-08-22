package io.github.sombreknight.feather.core;

import java.io.Serializable;

/**
 * 实体基类（泛型主键）
 *
 * <p>唯一强制约定：主键字段（泛型 {@code ID}）。继承即获得完整 CRUD 能力。</p>
 *
 * <pre>
 * &#064;Table("tb_user")
 * public class UserEntity extends BaseEntity&lt;Long&gt; {
 *     private String userName;   // 约定映射 user_name
 *     private OrderStatus status; // 枚举（CodeEnum）映射业务码
 *     private ExtInfo extInfo;    // 复杂对象自动 JSON 存储
 *     // getter / setter
 * }
 *
 * // 字符串主键（UUID）
 * &#064;Table("tb_order")
 * public class OrderEntity extends BaseEntity&lt;String&gt; {
 *     // ...
 * }
 * </pre>
 *
 * <p>主键生成：{@code Long} 主键默认使用雪花算法（{@link SnowflakeIdGenerator}），
 * {@code String} 主键默认使用 UUID（{@link UuidIdGenerator}）；JdbcDAO 按主键类型自动匹配，
 * 也可通过注册自定义 {@link IdGenerator} Bean 覆盖。</p>
 *
 * @param <ID> 主键类型（{@code Long} / {@code String} / 自定义类型）
 * @author sombreknight
 */
public abstract class BaseEntity<ID> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（泛型）
     *
     * <p>默认列名 id；如需其他列名（如 uid），通过 {@code @Table(idColumn = "uid")} 指定。</p>
     */
    private ID id;

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }
}
