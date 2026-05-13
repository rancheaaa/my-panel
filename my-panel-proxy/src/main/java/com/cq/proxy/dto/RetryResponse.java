package com.cq.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重试响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前状态 */
    private String status;

    /** 下次重试时间（时间戳毫秒） */
    private Long nextRetryAt;
}
