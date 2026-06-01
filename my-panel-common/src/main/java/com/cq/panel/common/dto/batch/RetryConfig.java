package com.cq.panel.common.dto.batch;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;

/**
 * 重试配置 (retryConfig)
 */
@Data
@NoArgsConstructor
public class RetryConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否启用重试 */
    private boolean enabled;

    /** 最大重试天数 */
    private Integer maxDays;

    /** 重试间隔(分钟) */
    private Integer intervalMin;

    /** 最大重试次数 */
    private Integer maxRetryCount;

    /** 退避类型: FIXED, LINEAR, EXPONENTIAL */
    private String backoffType;
}
