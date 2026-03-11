package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cq.panel.admin.server.common.annotation.Excel;
import com.cq.panel.admin.server.common.annotation.Excels;
import com.cq.panel.admin.server.repository.domain.SysDept;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "用户视图对象")
public class SysUserVO {
    @Schema(description = "用户ID", example = "1")
    @Excel(name = "用户序号", cellType = Excel.ColumnType.NUMERIC, prompt = "用户编号")
    private Long userId;

    @Schema(description = "部门ID", example = "100")
    @Excel(name = "部门编号")
    private Long deptId;

    @Schema(description = "用户账号", example = "admin")
    @Excel(name = "登录名称")
    private String userName;

    @Schema(description = "用户昵称", example = "管理员")
    @Excel(name = "用户名称")
    private String nickName;

    @Schema(description = "用户邮箱", example = "admin@example.com")
    @Excel(name = "用户邮箱")
    private String email;

    @Schema(description = "手机号码", example = "15888888888")
    @Excel(name = "手机号码", cellType = Excel.ColumnType.TEXT)
    private String phonenumber;

    @Schema(description = "用户性别（0男 1女 2未知）", example = "0")
    @Excel(name = "用户性别", readConverterExp = "0=男,1=女,2=未知")
    private String sex;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "帐号状态（0正常 1停用）", example = "0")
    @Excel(name = "帐号状态", readConverterExp = "0=正常,1=停用")
    private String status;

    @Schema(description = "删除标志（0代表存在 2代表删除）")
    private String delFlag;

    @Schema(description = "最后登录IP", example = "127.0.0.1")
    @Excel(name = "最后登录IP")
    private String loginIp;

    @Schema(description = "最后登录时间")
    @Excel(name = "最后登录时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date loginDate;

    @Schema(description = "部门对象")
    @Excels({
        @Excel(name = "部门名称", targetAttr = "deptName"),
        @Excel(name = "部门负责人", targetAttr = "leader")
    })
    private SysDept dept;
    
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

    @Schema(description = "角色ID")
    private Long roleId;
}
