package com.cq.proxy.controller.dto;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

/**
 * 重试响应
 */
@Data
public class RetryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前状态 */
    private String status;

    /** 下次重试时间（时间戳毫秒） */
    private Long nextRetryAt;

    public RetryResponse() {}

    public RetryResponse(String status, Long nextRetryAt) {
        this.status = status;
        this.nextRetryAt = nextRetryAt;
    }
}
