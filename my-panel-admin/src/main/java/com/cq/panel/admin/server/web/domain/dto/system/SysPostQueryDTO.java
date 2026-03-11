package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "岗位查询对象")
public class SysPostQueryDTO {
    @Schema(description = "岗位编码", example = "ceo")
    private String postCode;

    @Schema(description = "岗位名称", example = "董事长")
    private String postName;

    @Schema(description = "岗位状态（0正常 1停用）", example = "0")
    private String status;
}
