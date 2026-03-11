package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字典数据查询对象")
public class SysDictDataQueryDTO {
    @Schema(description = "字典标签", example = "男")
    private String dictLabel;

    @Schema(description = "字典类型", example = "sys_user_sex")
    private String dictType;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;
}
