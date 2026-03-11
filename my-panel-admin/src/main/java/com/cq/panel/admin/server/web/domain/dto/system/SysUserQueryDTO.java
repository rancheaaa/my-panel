package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Map;

@Data
@Schema(description = "用户查询对象")
public class SysUserQueryDTO {
    @Schema(description = "用户账号", example = "admin")
    private String userName;

    @Schema(description = "手机号码", example = "15888888888")
    private String phonenumber;

    @Schema(description = "帐号状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "部门ID", example = "100")
    private Long deptId;
    
    @Schema(description = "角色ID", example = "1")
    private Long roleId; 

    @Schema(description = "请求参数")
    private Map<String, Object> params;
}
