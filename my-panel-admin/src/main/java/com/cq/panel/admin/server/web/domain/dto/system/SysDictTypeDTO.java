package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "字典类型添加/修改对象")
public class SysDictTypeDTO {
    @Schema(description = "字典ID", example = "1")
    private Long dictId;

    @Schema(description = "字典名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户性别")
    @NotBlank(message = "字典名称不能为空")
    @Size(min = 0, max = 100, message = "字典类型名称长度不能超过100个字符")
    private String dictName;

    @Schema(description = "字典类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_user_sex")
    @NotBlank(message = "字典类型不能为空")
    @Size(min = 0, max = 100, message = "字典类型类型长度不能超过100个字符")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字典类型必须以字母开头，且只能为（小写字母，数字，下滑线）")
    private String dictType;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;
    
    @Schema(description = "备注", example = "用户性别列表")
    private String remark;
}
