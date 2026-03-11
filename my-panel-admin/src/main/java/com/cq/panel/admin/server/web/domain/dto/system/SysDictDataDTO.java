package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "字典数据添加/修改对象")
public class SysDictDataDTO {
    @Schema(description = "字典编码", example = "1")
    private Long dictCode;

    @Schema(description = "字典排序", example = "1")
    private Long dictSort;

    @Schema(description = "字典标签", requiredMode = Schema.RequiredMode.REQUIRED, example = "男")
    @NotBlank(message = "字典标签不能为空")
    @Size(min = 0, max = 100, message = "字典标签长度不能超过100个字符")
    private String dictLabel;

    @Schema(description = "字典键值", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotBlank(message = "字典键值不能为空")
    @Size(min = 0, max = 100, message = "字典键值长度不能超过100个字符")
    private String dictValue;

    @Schema(description = "字典类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_user_sex")
    @NotBlank(message = "字典类型不能为空")
    @Size(min = 0, max = 100, message = "字典类型长度不能超过100个字符")
    private String dictType;

    @Schema(description = "样式属性（其他样式扩展）", example = "red")
    @Size(min = 0, max = 100, message = "样式属性长度不能超过100个字符")
    private String cssClass;

    @Schema(description = "表格回显样式（default、primary、success、info、warning、danger）", example = "default")
    private String listClass;

    @Schema(description = "是否默认（Y是 N否）", example = "N")
    private String isDefault;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "备注", example = "性别男")
    private String remark;
}
