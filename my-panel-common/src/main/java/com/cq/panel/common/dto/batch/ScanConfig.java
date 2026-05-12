package com.cq.panel.common.dto.batch;

import java.io.Serial;
import java.io.Serializable;

/**
 * 扫描配置 (scanConfig)
 */
public class ScanConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Cron表达式 */
    private String cronExpression;

    /** 最大扫描文件数 */
    private Integer maxScanFiles;

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Integer getMaxScanFiles() {
        return maxScanFiles;
    }

    public void setMaxScanFiles(Integer maxScanFiles) {
        this.maxScanFiles = maxScanFiles;
    }
}
