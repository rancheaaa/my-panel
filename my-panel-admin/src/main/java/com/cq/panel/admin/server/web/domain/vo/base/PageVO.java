package com.cq.panel.admin.server.web.domain.vo.base;

import lombok.Data;
import java.util.List;

@Data
public class PageVO<T> {
    private long total;
    private List<T> rows;
    
    public PageVO(List<T> rows, long total) {
        this.rows = rows;
        this.total = total;
    }
}

