package com.cq.panel.admin.server.web.domain.vo.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * 环境管理 VO
 * 
 * @author cq
 */
@Data
@Schema(description = "环境管理VO")
public class RcEnvVO {
    @Schema(description = "环境ID")
    private Long id;

    @Schema(description = "环境名称")
    private String envName;

    @Schema(description = "环境描述")
    private String envDesc;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;
}
