package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class BatchAlertEvent extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String agentId;
    private Long taskId;
    private String alertLevel;
    private String alertCategory;
    private String alertTitle;
    private String alertMessage;
    private String metricsSnapshot;
    private Integer isResolved;
    private String resolvedBy;
    private Date resolvedAt;
    private String resolutionNote;
    private Integer notificationSent;
    private String notificationChannels;
}
