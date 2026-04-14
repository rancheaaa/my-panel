package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 操作日志记录视图对象
 * 
 * @author cq
 */
@Data
@Schema(description = "操作日志记录视图对象")
public class SysOperLogVO {
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志主键")
    @Excel(name = "操作序号", cellType = Excel.ColumnType.NUMERIC)
    private Long operId;

    @Schema(description = "操作模块")
    @Excel(name = "操作模块")
    private String title;

    @Schema(description = "业务类型（0其它 1新增 2修改 3删除）")
    @Excel(name = "业务类型", readConverterExp = "0=其它,1=新增,2=修改,3=删除,4=授权,5=导出,6=导入,7=强退,8=生成代码,9=清空数据")
    private Integer businessType;

    @Schema(description = "请求方法")
    @Excel(name = "请求方法")
    private String method;

    @Schema(description = "请求方式")
    @Excel(name = "请求方式")
    private String requestMethod;

    @Schema(description = "操作类别（0其它 1后台用户 2手机端用户）")
    @Excel(name = "操作类别", readConverterExp = "0=其它,1=后台用户,2=手机端用户")
    private Integer operatorType;

    @Schema(description = "操作人员")
    @Excel(name = "操作人员")
    private String operName;

    @Schema(description = "部门名称")
    @Excel(name = "部门名称")
    private String deptName;

    @Schema(description = "请求url")
    @Excel(name = "请求地址")
    private String operUrl;

    @Schema(description = "操作地址")
    @Excel(name = "操作地址")
    private String operIp;

    @Schema(description = "操作地点")
    @Excel(name = "操作地点")
    private String operLocation;

    @Schema(description = "请求参数")
    @Excel(name = "请求参数")
    private String operParam;

    @Schema(description = "返回参数")
    @Excel(name = "返回参数")
    private String jsonResult;

    @Schema(description = "操作状态（0正常 1异常）")
    @Excel(name = "状态", readConverterExp = "0=正常,1=异常")
    private Integer status;

    @Schema(description = "错误消息")
    @Excel(name = "错误消息")
    private String errorMsg;

    @Schema(description = "操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "操作时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date operTime;

    @Schema(description = "消耗时间")
    @Excel(name = "消耗时间", suffix = "毫秒")
    private Long costTime;
}

