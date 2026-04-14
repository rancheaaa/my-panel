package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "岗位排序对象")
public class SysPostSortDTO {
    @Schema(description = "岗位ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "岗位ID不能为空")
    private Long postId;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "显示顺序不能为空")
    private Integer postSort;
}