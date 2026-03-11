package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;

@Schema(description = "部门视图对象")
public record SysDeptVO(
    @Schema(description = "部门ID", example = "100")
    Long deptId,
    
    @Schema(description = "父部门ID", example = "0")
    Long parentId,
    
    @Schema(description = "祖级列表", example = "0,100")
    String ancestors,
    
    @Schema(description = "部门名称", example = "研发部门")
    String deptName,
    
    @Schema(description = "显示顺序", example = "1")
    Integer orderNum,
    
    @Schema(description = "负责人", example = "张三")
    String leader,
    
    @Schema(description = "联系电话", example = "15888888888")
    String phone,
    
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    String email,
    
    @Schema(description = "部门状态（0正常 1停用）", example = "0")
    String status,
    
    @Schema(description = "父部门名称", example = "若依科技")
    String parentName,
    
    @Schema(description = "创建者")
    String createBy,
    
    @Schema(description = "创建时间")
    Date createTime,
    
    @Schema(description = "更新者")
    String updateBy,
    
    @Schema(description = "更新时间")
    Date updateTime,
    
    @Schema(description = "子部门列表")
    List<SysDeptVO> children
) {}
