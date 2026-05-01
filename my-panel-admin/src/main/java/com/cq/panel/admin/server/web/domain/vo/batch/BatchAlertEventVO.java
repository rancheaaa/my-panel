package com.cq.panel.admin.server.web.domain.vo.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "批量传输告警事件VO")
public class BatchAlertEventVO
{
    @Schema(description = "告警ID")
    private Long id;

    @Schema(description = "Agent ID")
    private String agentId;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "告警级别")
    private String alertLevel;

    @Schema(description = "告警分类")
    private String alertCategory;

    @Schema(description = "告警标题")
    private String alertTitle;

    @Schema(description = "告警消息")
    private String alertMessage;

    @Schema(description = "是否已解决")
    private Integer isResolved;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "告警时间")
    private Date createTime;
}
