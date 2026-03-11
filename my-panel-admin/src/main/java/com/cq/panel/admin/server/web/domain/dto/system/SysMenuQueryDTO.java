package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "菜单查询对象")
public class SysMenuQueryDTO {
    @Schema(description = "菜单名称", example = "用户管理")
    private String menuName;

    @Schema(description = "菜单状态（0显示 1隐藏）", example = "0")
    private String visible;

    @Schema(description = "菜单状态（0正常 1停用）", example = "0")
    private String status;
}
