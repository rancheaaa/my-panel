package com.cq.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 批量操作响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 更新的记录数 */
    private Integer updatedCount;
}
