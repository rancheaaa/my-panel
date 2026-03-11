package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "岗位视图对象")
public record SysPostVO(
    @Schema(description = "岗位ID", example = "1")
    Long postId,
    
    @Schema(description = "岗位编码", example = "ceo")
    String postCode,
    
    @Schema(description = "岗位名称", example = "董事长")
    String postName,
    
    @Schema(description = "显示顺序", example = "1")
    Integer postSort,
    
    @Schema(description = "岗位状态（0正常 1停用）", example = "0")
    String status,
    
    @Schema(description = "备注", example = "CEO")
    String remark,
    
    @Schema(description = "创建者")
    String createBy,
    
    @Schema(description = "创建时间")
    Date createTime,
    
    @Schema(description = "更新者")
    String updateBy,
    
    @Schema(description = "更新时间")
    Date updateTime
) {}
