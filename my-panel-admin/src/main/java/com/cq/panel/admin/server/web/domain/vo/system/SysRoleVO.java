package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cq.panel.admin.server.common.annotation.Excel;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "角色视图对象")
public class SysRoleVO {
    @Schema(description = "角色ID", example = "1")
    @Excel(name = "角色序号", cellType = Excel.ColumnType.NUMERIC)
    private Long roleId;

    @Schema(description = "角色名称", example = "管理员")
    @Excel(name = "角色名称")
    private String roleName;

    @Schema(description = "权限字符", example = "admin")
    @Excel(name = "角色权限")
    private String roleKey;

    @Schema(description = "显示顺序", example = "1")
    @Excel(name = "角色排序")
    private Integer roleSort;

    @Schema(description = "数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）", example = "1")
    @Excel(name = "数据范围", readConverterExp = "1=所有数据权限,2=自定义数据权限,3=本部门数据权限,4=本部门及以下数据权限,5=仅本人数据权限")
    private String dataScope;

    @Schema(description = "菜单树选择项是否关联显示", example = "true")
    private boolean menuCheckStrictly;

    @Schema(description = "部门树选择项是否关联显示", example = "true")
    private boolean deptCheckStrictly;

    @Schema(description = "角色状态（0正常 1停用）", example = "0")
    @Excel(name = "角色状态", readConverterExp = "0=正常,1=停用")
    private String status;

    @Schema(description = "用户是否存在此角色标识", example = "true")
    private boolean flag;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "备注")
    private String remark;
}
