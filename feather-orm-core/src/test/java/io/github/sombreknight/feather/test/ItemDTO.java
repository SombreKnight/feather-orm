package io.github.sombreknight.feather.test;

import java.math.BigDecimal;

/**
 * 查询投影 DTO（findDto* 测试用）
 *
 * <p>{@code notInResult} 字段在查询结果中不存在，DTO 映射应自动跳过而非报错。</p>
 *
 * @author sombreknight
 */
public class ItemDTO {

    private String name;

    private BigDecimal price;

    private String notInResult;

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

    public String getNotInResult() {
        return notInResult;
    }

    public void setNotInResult(String notInResult) {
        this.notInResult = notInResult;
    }
}
