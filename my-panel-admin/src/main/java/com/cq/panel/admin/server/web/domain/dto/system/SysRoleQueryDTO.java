package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Map;

@Data
@Schema(description = "角色查询对象")
public class SysRoleQueryDTO {
    @Schema(description = "角色名称", example = "管理员")
    private String roleName;

    @Schema(description = "权限字符", example = "admin")
    private String roleKey;

    @Schema(description = "角色状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "请求参数")
    private Map<String, Object> params;
}
