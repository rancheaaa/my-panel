package com.cq.panel.admin.server.web.domain.vo.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * AccessToken管理 VO
 * 
 * @author cq
 */
@Data
@Schema(description = "AccessToken管理VO")
public class RcAccessTokenVO {
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Token值")
    private String tokenValue;

    @Schema(description = "Token描述")
    private String tokenDesc;

    @Schema(description = "状态（0启用 1禁用）")
    private String status;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;
}
