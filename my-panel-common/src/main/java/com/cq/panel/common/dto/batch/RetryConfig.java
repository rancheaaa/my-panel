package com.cq.panel.common.dto.batch;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重试配置 (retryConfig)
 */
public class RetryConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否启用重试 */
    private Boolean enabled;

    /** 最大重试天数 */
    private Integer maxDays;

    /** 重试间隔(分钟) */
    private Integer intervalMin;

    /** 最大重试次数 */
    private Integer maxRetryCount;

    /** 退避类型: FIXED, LINEAR, EXPONENTIAL */
    private String backoffType;

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(Integer maxDays) {
        this.maxDays = maxDays;
    }

    public Integer getIntervalMin() {
        return intervalMin;
    }

    public void setIntervalMin(Integer intervalMin) {
        this.intervalMin = intervalMin;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public String getBackoffType() {
        return backoffType;
    }

    public void setBackoffType(String backoffType) {
        this.backoffType = backoffType;
    }
}
