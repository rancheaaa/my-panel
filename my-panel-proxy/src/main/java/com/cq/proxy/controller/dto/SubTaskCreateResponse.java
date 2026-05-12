package com.cq.proxy.controller.dto;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

/**
 * 子任务创建成功响应
 */
@Data
public class SubTaskCreateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建后的子任务ID（数据库主键） */
    private Long subtaskId;

    public SubTaskCreateResponse() {}

    public SubTaskCreateResponse(Long subtaskId) {
        this.subtaskId = subtaskId;
    }
}
