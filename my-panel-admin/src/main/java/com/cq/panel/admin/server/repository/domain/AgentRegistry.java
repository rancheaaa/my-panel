package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.annotation.Excel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

/**
 * Agent注册信息对象 agent_registry
 * 
 * @author cq
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class AgentRegistry extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private String id;

    /** 节点名称 */
    @Excel(name = "节点名称")
    private String nodeName;

    /** 所属操作系统 */
    @Excel(name = "操作系统")
    private String osType;

    /** 所属应用ID */
    @Excel(name = "应用ID")
    private String appId;

    /** Agent IP地址 */
    @Excel(name = "Agent IP")
    private String agentIp;

    /** Agent端口 */
    @Excel(name = "Agent端口")
    private Integer agentPort;

    /** 节点是否启用 */
    @Excel(name = "节点启用", readConverterExp = "0=启用,1=临时关闭,2=永久关闭")
    private Integer nodeEnabled;

    /** 节点状态 */
    @Excel(name = "节点状态", readConverterExp = "0=离线,1=在线,2=未知")
    private Integer nodeStatus;

    /** 备注信息 */
    @Excel(name = "备注")
    private String remark;

    /** 最后刷新时间 */
    @Excel(name = "最后刷新时间")
    private java.util.Date lastRefreshTime;
}