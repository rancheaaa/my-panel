package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 系统访问记录查询对象
 * 
 * @author cq
 */
@Data
@Schema(description = "系统访问记录查询对象")
public class SysLogininforQueryDTO {

    @Schema(description = "登录IP地址")
    private String ipaddr;

    @Schema(description = "登录状态 0成功 1失败")
    private String status;

    @Schema(description = "用户账号")
    private String userName;
    
    @Schema(description = "请求参数")
    private Map<String, Object> params;
}

