package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "用户添加/修改对象")
public class SysUserDTO {
    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 0, max = 30, message = "用户账号长度不能超过30个字符")
    private String userName;

    @Schema(description = "用户昵称", example = "管理员")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过30个字符")
    private String nickName;

    @Schema(description = "用户邮箱", example = "admin@example.com")
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    @Schema(description = "手机号码", example = "15888888888")
    @Size(min = 0, max = 11, message = "手机号码长度不能超过11个字符")
    private String phonenumber;

    @Schema(description = "用户性别（0男 1女 2未知）", example = "0")
    private String sex;

    @Schema(description = "密码", example = "123456")
    private String password;

    @Schema(description = "帐号状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "备注", example = "这是管理员账号")
    private String remark;

    @Schema(description = "岗位组")
    private Long[] postIds;

    @Schema(description = "角色组")
    private Long[] roleIds;
}
