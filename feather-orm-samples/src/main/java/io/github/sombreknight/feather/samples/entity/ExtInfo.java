package io.github.sombreknight.feather.samples.entity;

/**
 * 扩展信息：作为 JSON 列存储
 *
 * @author sombreknight
 */
public class ExtInfo {

    private Integer level;
    private String remark;

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
