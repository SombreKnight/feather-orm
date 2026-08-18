package io.github.sombreknight.feather.core;

import java.io.Serializable;

/**
 * 分页信息
 *
 * @author sombreknight
 */
public class PageInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long totalPage;
    private int page;
    private int size;

    public PageInfo() {
    }

    public PageInfo(long total, int page, int size) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPage = size == 0 ? 0 : (total + size - 1) / size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getTotalPage() {
        return totalPage;
    }

    public void setTotalPage(long totalPage) {
        this.totalPage = totalPage;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
