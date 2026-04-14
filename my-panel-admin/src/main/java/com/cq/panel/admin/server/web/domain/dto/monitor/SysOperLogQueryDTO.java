package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 操作日志记录查询对象
 * 
 * @author cq
 */
@Data
@Schema(description = "操作日志记录查询对象")
public class SysOperLogQueryDTO {

    @Schema(description = "操作模块")
    private String title;

    @Schema(description = "操作人员")
    private String operName;

    @Schema(description = "业务类型")
    private Integer businessType;

    @Schema(description = "业务类型数组")
    private Integer[] businessTypes;

    @Schema(description = "操作状态")
    private Integer status;

    @Schema(description = "操作地址")
    private String operIp;

    @Schema(description = "开始时间")
    private String beginTime;

    @Schema(description = "结束时间")
    private String endTime;

    @Schema(description = "请求参数")
    private Map<String, Object> params;
}