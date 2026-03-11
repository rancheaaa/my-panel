package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "部门添加/修改对象")
public class SysDeptDTO {
    @Schema(description = "部门ID", example = "100")
    private Long deptId;

    @Schema(description = "父部门ID", example = "0")
    private Long parentId;

    @Schema(description = "祖级列表", example = "0,100")
    private String ancestors;

    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "研发部门")
    @NotBlank(message = "部门名称不能为空")
    @Size(min = 0, max = 30, message = "部门名称长度不能超过30个字符")
    private String deptName;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "显示顺序不能为空")
    private Integer orderNum;

    @Schema(description = "负责人", example = "张三")
    private String leader;

    @Schema(description = "联系电话", example = "15888888888")
    @Size(min = 0, max = 11, message = "联系电话长度不能超过11个字符")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    @Schema(description = "部门状态（0正常 1停用）", example = "0")
    private String status;
}
