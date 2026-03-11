package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;

@Schema(description = "菜单视图对象")
public record SysMenuVO(
    @Schema(description = "菜单ID", example = "1")
    Long menuId,
    
    @Schema(description = "菜单名称", example = "用户管理")
    String menuName,
    
    @Schema(description = "父菜单名称", example = "系统管理")
    String parentName,
    
    @Schema(description = "父菜单ID", example = "0")
    Long parentId,
    
    @Schema(description = "显示顺序", example = "1")
    Integer orderNum,
    
    @Schema(description = "路由地址", example = "user")
    String path,
    
    @Schema(description = "组件路径", example = "system/user/index")
    String component,
    
    @Schema(description = "路由参数", example = "{\"id\": 1}")
    String query,
    
    @Schema(description = "路由名称", example = "User")
    String routeName,
    
    @Schema(description = "是否为外链（0是 1否）", example = "1")
    String isFrame,
    
    @Schema(description = "是否缓存（0缓存 1不缓存）", example = "0")
    String isCache,
    
    @Schema(description = "菜单类型（M目录 C菜单 F按钮）", example = "C")
    String menuType,
    
    @Schema(description = "菜单状态（0显示 1隐藏）", example = "0")
    String visible,
    
    @Schema(description = "菜单状态（0正常 1停用）", example = "0")
    String status,
    
    @Schema(description = "权限标识", example = "system:user:list")
    String perms,
    
    @Schema(description = "菜单图标", example = "user")
    String icon,
    
    @Schema(description = "创建者")
    String createBy,
    
    @Schema(description = "创建时间")
    Date createTime,
    
    @Schema(description = "更新者")
    String updateBy,
    
    @Schema(description = "更新时间")
    Date updateTime,
    
    @Schema(description = "子菜单列表")
    List<SysMenuVO> children
) {}
