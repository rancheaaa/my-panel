package com.cq.panel.common.dto.batch;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;

/**
 * 扫描配置 (scanConfig)
 */
@Data
@NoArgsConstructor
public class ScanConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Cron表达式 */
    private String cronExpression;

    /** 最大扫描文件数 */
    private Integer maxScanFiles;
}
