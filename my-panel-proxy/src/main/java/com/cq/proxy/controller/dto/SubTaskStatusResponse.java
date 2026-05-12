package com.cq.proxy.controller.dto;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

/**
 * 子任务状态响应
 */
@Data
public class SubTaskStatusResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前状态 */
    private String status;

    public SubTaskStatusResponse() {}

    public SubTaskStatusResponse(String status) {
        this.status = status;
    }
}
