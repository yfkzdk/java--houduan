package com.yfkzdk.chatbot.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 分页响应封装
 */
public class CommonPage<T> {
    private Long pageNum;
    private Long pageSize;
    private Long totalPage;
    private Long total;
    private List<T> list;

    public static <T> CommonPage<T> restPage(Page<T> pageResult) {
        CommonPage<T> result = new CommonPage<>();
        result.setPageNum(pageResult.getCurrent());
        result.setPageSize(pageResult.getSize());
        result.setTotalPage(pageResult.getPages());
        result.setTotal(pageResult.getTotal());
        result.setList(pageResult.getRecords());
        return result;
    }

    public Long getPageNum() { return pageNum; }
    public void setPageNum(Long pageNum) { this.pageNum = pageNum; }
    public Long getPageSize() { return pageSize; }
    public void setPageSize(Long pageSize) { this.pageSize = pageSize; }
    public Long getTotalPage() { return totalPage; }
    public void setTotalPage(Long totalPage) { this.totalPage = totalPage; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
}
