package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 系统访问记录视图对象
 * 
 * @author cq
 */
@Data
@Schema(description = "系统访问记录视图对象")
public class SysLogininforVO {
    private static final long serialVersionUID = 1L;

    @Schema(description = "访问ID")
    @Excel(name = "序号", cellType = Excel.ColumnType.NUMERIC)
    private Long infoId;

    @Schema(description = "用户账号")
    @Excel(name = "用户账号")
    private String userName;

    @Schema(description = "登录状态 0成功 1失败")
    @Excel(name = "登录状态", readConverterExp = "0=成功,1=失败")
    private String status;

    @Schema(description = "登录IP地址")
    @Excel(name = "登录地址")
    private String ipaddr;

    @Schema(description = "登录地点")
    @Excel(name = "登录地点")
    private String loginLocation;

    @Schema(description = "浏览器类型")
    @Excel(name = "浏览器")
    private String browser;

    @Schema(description = "操作系统")
    @Excel(name = "操作系统")
    private String os;

    @Schema(description = "提示消息")
    @Excel(name = "提示消息")
    private String msg;

    @Schema(description = "访问时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "访问时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date loginTime;
}

