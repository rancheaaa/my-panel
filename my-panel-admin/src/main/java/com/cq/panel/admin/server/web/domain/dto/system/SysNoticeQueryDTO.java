package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通知公告查询对象")
public class SysNoticeQueryDTO {
    @Schema(description = "公告标题", example = "系统维护")
    private String noticeTitle;

    @Schema(description = "公告类型（1通知 2公告）", example = "1")
    private String noticeType;

    @Schema(description = "创建者", example = "admin")
    private String createBy;
}
