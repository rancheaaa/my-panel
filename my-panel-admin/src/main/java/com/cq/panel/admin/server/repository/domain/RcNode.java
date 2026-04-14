package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 注册中心节点对象 rc_node
 * 
 * @author cq
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class RcNode extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 环境ID */
    @Excel(name = "环境ID")
    private Long envId;

    /** 项目ID */
    @Excel(name = "项目ID")
    private Long projectId;

    /** 节点IP */
    @Excel(name = "节点IP")
    private String nodeIp;

    /** 节点端口 */
    @Excel(name = "节点端口")
    private Integer nodePort;

    /** 状态（0在线 1离线） */
    @Excel(name = "状态", readConverterExp = "0=在线,1=离线")
    private String status;

    /** 服务所在区域 */
    @Excel(name = "服务区域")
    private String zone;

    /** 最后刷新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最后刷新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastRefreshTime;

    /** 环境名称 */
    private String envName;

    /** 项目名称 */
    private String projectName;
}