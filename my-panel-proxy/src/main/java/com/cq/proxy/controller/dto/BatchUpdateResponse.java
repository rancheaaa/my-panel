package com.cq.proxy.controller.dto;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

/**
 * 批量操作响应
 */
@Data
public class BatchUpdateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 更新的记录数 */
    private Integer updatedCount;

    public BatchUpdateResponse() {}

    public BatchUpdateResponse(Integer updatedCount) {
        this.updatedCount = updatedCount;
    }
}
