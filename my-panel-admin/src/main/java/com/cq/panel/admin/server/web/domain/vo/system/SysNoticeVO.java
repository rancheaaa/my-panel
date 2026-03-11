package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "通知公告视图对象")
public class SysNoticeVO {
    @Schema(description = "公告ID", example = "1")
    private Long noticeId;

    @Schema(description = "公告标题", example = "系统维护通知")
    private String noticeTitle;

    @Schema(description = "公告类型（1通知 2公告）", example = "1")
    private String noticeType;

    @Schema(description = "公告内容", example = "系统将于今晚进行维护")
    private String noticeContent;

    @Schema(description = "公告状态（0正常 1关闭）", example = "0")
    private String status;

    @Schema(description = "备注", example = "重要通知")
    private String remark;

    @Schema(description = "创建者", example = "admin")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;
}
