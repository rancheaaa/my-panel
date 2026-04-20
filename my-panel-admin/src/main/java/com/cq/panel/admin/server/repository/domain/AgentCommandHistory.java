package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class AgentCommandHistory extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** Agent节点ID */
    @Excel(name = "Agent节点ID")
    private String agentId;

    /** Agent节点名称 */
    @Excel(name = "Agent节点名称")
    private String agentName;

    /** Agent IP地址 */
    @Excel(name = "Agent IP")
    private String agentIp;

    /** Agent端口 */
    @Excel(name = "Agent端口")
    private Integer agentPort;

    /** 执行的命令内容 */
    @Excel(name = "命令内容")
    private String command;

    /** 命令超时时间（秒） */
    @Excel(name = "超时时间(秒)")
    private Integer commandTimeout;

    /** 命令执行状态（0-成功 1-失败 2-超时 3-未知） */
    @Excel(name = "执行状态", readConverterExp = "0=成功,1=失败,2=超时,3=未知")
    private Integer commandStatus;

    /** 进程退出码 */
    @Excel(name = "退出码")
    private Integer exitCode;

    /** 标准输出内容 */
    private String output;

    /** 错误输出内容 */
    private String error;

    /** 执行耗时（毫秒） */
    @Excel(name = "耗时(ms)")
    private Long executeTime;

    /** 命令提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date submitTime;

    /** 命令开始执行时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date startTime;

    /** 命令完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date endTime;

    /** 操作用户ID */
    private Long userId;

    /** 操作用户名 */
    @Excel(name = "操作用户")
    private String userName;
}
