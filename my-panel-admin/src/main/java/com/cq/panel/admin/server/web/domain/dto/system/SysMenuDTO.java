package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "菜单添加/修改对象")
public class SysMenuDTO {
    @Schema(description = "菜单ID", example = "1")
    private Long menuId;

    @Schema(description = "父菜单ID", example = "0")
    private Long parentId;

    @Schema(description = "父菜单名称", example = "系统管理")
    private String parentName;

    @Schema(description = "菜单名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户管理")
    @NotBlank(message = "菜单名称不能为空")
    @Size(min = 0, max = 50, message = "菜单名称长度不能超过50个字符")
    private String menuName;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "显示顺序不能为空")
    private Integer orderNum;

    @Schema(description = "路由地址", example = "user")
    @Size(min = 0, max = 200, message = "路由地址不能超过200个字符")
    private String path;

    @Schema(description = "组件路径", example = "system/user/index")
    @Size(min = 0, max = 200, message = "组件路径不能超过255个字符")
    private String component;

    @Schema(description = "路由参数", example = "{\"id\": 1}")
    private String query;

    @Schema(description = "路由名称", example = "User")
    private String routeName;

    @Schema(description = "是否为外链（0是 1否）", example = "1")
    private String isFrame;

    @Schema(description = "是否缓存（0缓存 1不缓存）", example = "0")
    private String isCache;

    @Schema(description = "菜单类型（M目录 C菜单 F按钮）", requiredMode = Schema.RequiredMode.REQUIRED, example = "C")
    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    @Schema(description = "菜单状态（0显示 1隐藏）", example = "0")
    private String visible;

    @Schema(description = "菜单状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "权限标识", example = "system:user:list")
    @Size(min = 0, max = 100, message = "权限标识长度不能超过100个字符")
    private String perms;

    @Schema(description = "菜单图标", example = "user")
    private String icon;

    @Schema(description = "备注", example = "用户管理菜单")
    private String remark;
}
