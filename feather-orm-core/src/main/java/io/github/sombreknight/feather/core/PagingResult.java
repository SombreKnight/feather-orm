package io.github.sombreknight.feather.core;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 分页查询结果
 *
 * @param <T> 数据元素类型
 * @author sombreknight
 */
public class PagingResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private PageInfo pageInfo;
    private List<T> data;

    public PagingResult() {
    }

    public PagingResult(PageInfo pageInfo, List<T> data) {
        this.pageInfo = pageInfo;
        this.data = data;
    }

    public PageInfo getPageInfo() {
        return pageInfo;
    }

    public void setPageInfo(PageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public boolean isEmpty() {
        return data == null || data.isEmpty();
    }

    @Override
    public String toString() {
        return "PagingResult{pageInfo=" + pageInfo + ", data.size=" + (data == null ? 0 : data.size()) + '}';
    }
}
