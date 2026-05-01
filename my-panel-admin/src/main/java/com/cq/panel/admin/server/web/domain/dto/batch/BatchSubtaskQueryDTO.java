package com.cq.panel.admin.server.web.domain.dto.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "批量子任务查询DTO")
public class BatchSubtaskQueryDTO
{
    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "子任务状态")
    private String status;

    @Schema(description = "目标Agent ID")
    private String targetAgentId;

    @Schema(description = "搜索关键词(文件名)")
    private String searchKeyword;
}
