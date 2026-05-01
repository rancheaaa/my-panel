package com.cq.panel.admin.server.web.domain.dto.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "批量传输任务查询DTO")
public class BatchTaskQueryDTO
{
    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "源Agent ID")
    private String sourceAgentId;

    @Schema(description = "关键词搜索(任务名称)")
    private String keyword;

    @Schema(description = "创建时间起")
    private Date dateFrom;

    @Schema(description = "创建时间止")
    private Date dateTo;
}
