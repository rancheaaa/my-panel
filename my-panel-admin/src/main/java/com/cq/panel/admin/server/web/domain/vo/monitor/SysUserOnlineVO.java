package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前在线会话视图对象
 * 
 * @author cq
 */
@Data
@Schema(description = "当前在线会话视图对象")
public class SysUserOnlineVO {

    @Schema(description = "会话编号")
    private String tokenId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "登录IP地址")
    private String ipaddr;

    @Schema(description = "登录地址")
    private String loginLocation;

    @Schema(description = "浏览器类型")
    private String browser;

    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "登录时间")
    private Long loginTime;
}

