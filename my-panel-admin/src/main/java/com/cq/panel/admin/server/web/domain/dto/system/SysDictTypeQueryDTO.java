package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字典类型查询对象")
public class SysDictTypeQueryDTO {
    @Schema(description = "字典名称", example = "用户性别")
    private String dictName;

    @Schema(description = "字典类型", example = "sys_user_sex")
    private String dictType;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "开始时间", example = "2024-01-01")
    private String beginTime;

    @Schema(description = "结束时间", example = "2024-12-31")
    private String endTime;

    public String getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(String beginTime) {
        this.beginTime = beginTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
