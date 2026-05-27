package com.cq.panel.common.dto.batch;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
public class ScanConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String cronExpression;

    private Integer maxScanFiles;

    private Boolean scheduledEnabled;

    /** 每日定时传输开始时间(HH:mm:ss) */
    private String scheduledStartTime;

    /** 每日定时传输结束时间(HH:mm:ss) */
    private String scheduledEndTime;
}
