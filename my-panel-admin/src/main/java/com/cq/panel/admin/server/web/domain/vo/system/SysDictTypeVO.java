package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "字典类型视图对象")
public record SysDictTypeVO(
    @Schema(description = "字典主键", example = "1")
    Long dictId,

    @Schema(description = "字典名称", example = "用户性别")
    String dictName,

    @Schema(description = "字典类型", example = "sys_user_sex")
    String dictType,

    @Schema(description = "状态（0正常 1停用）", example = "0")
    String status,

    @Schema(description = "备注", example = "用户性别列表")
    String remark,

    @Schema(description = "创建者")
    String createBy,
    
    @Schema(description = "创建时间")
    Date createTime,
    
    @Schema(description = "更新者")
    String updateBy,
    
    @Schema(description = "更新时间")
    Date updateTime
) {}
