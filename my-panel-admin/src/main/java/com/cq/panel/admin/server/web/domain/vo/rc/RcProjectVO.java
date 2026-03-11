package com.cq.panel.admin.server.web.domain.vo.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * 应用管理 VO
 * 
 * @author cq
 */
@Data
@Schema(description = "应用管理VO")
public class RcProjectVO {
    @Schema(description = "应用ID")
    private Long id;

    @Schema(description = "应用名称")
    private String projectName;

    @Schema(description = "应用描述")
    private String projectDesc;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;
}
