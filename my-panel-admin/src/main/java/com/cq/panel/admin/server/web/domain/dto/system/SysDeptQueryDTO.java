package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "部门查询对象")
public class SysDeptQueryDTO {
    @Schema(description = "部门ID", example = "100")
    private Long deptId;

    @Schema(description = "父部门ID", example = "0")
    private Long parentId;

    @Schema(description = "部门名称", example = "研发部门")
    private String deptName;

    @Schema(description = "部门状态（0正常 1停用）", example = "0")
    private String status;
}
