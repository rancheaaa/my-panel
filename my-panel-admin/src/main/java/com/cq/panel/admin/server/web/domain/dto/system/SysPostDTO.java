package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "岗位添加/修改对象")
public class SysPostDTO {
    @Schema(description = "岗位ID", example = "1")
    private Long postId;

    @Schema(description = "岗位编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "ceo")
    @NotBlank(message = "岗位编码不能为空")
    @Size(min = 0, max = 64, message = "岗位编码长度不能超过64个字符")
    private String postCode;

    @Schema(description = "岗位名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "董事长")
    @NotBlank(message = "岗位名称不能为空")
    @Size(min = 0, max = 50, message = "岗位名称长度不能超过50个字符")
    private String postName;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "显示顺序不能为空")
    private Integer postSort;

    @Schema(description = "岗位状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "备注", example = "CEO")
    private String remark;
}
