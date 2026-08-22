package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 高级测试实体（JdbcDAO 低层 API 测试用）
 *
 * @author sombreknight
 */
@Table("tb_item")
public class ItemEntity extends BaseEntity<Long> {

    private String name;

    private BigDecimal price;

    /** 可空字段：用于批量插入分组测试 */
    private String note;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
