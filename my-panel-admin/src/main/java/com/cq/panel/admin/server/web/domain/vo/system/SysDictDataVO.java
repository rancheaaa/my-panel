package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "字典数据视图对象")
public record SysDictDataVO(
    @Schema(description = "字典编码", example = "1")
    Long dictCode,

    @Schema(description = "字典排序", example = "1")
    Long dictSort,

    @Schema(description = "字典标签", example = "男")
    String dictLabel,

    @Schema(description = "字典键值", example = "0")
    String dictValue,

    @Schema(description = "字典类型", example = "sys_user_sex")
    String dictType,

    @Schema(description = "样式属性", example = "red")
    String cssClass,

    @Schema(description = "表格回显样式", example = "default")
    String listClass,

    @Schema(description = "是否默认", example = "N")
    String isDefault,

    @Schema(description = "状态", example = "0")
    String status,

    @Schema(description = "备注", example = "性别男")
    String remark,

    @Schema(description = "创建时间")
    Date createTime
) {}
