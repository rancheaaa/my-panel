package com.cq.panel.admin.server.web.domain.vo.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * 注册中心节点 VO
 * 
 * @author cq
 */
@Data
@Schema(description = "注册中心节点VO")
public class RcNodeVO {
    @Schema(description = "节点ID")
    private Long id;

    @Schema(description = "环境ID")
    private Long envId;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "环境名称")
    private String envName;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "节点IP")
    private String nodeIp;

    @Schema(description = "节点端口")
    private Integer nodePort;

    @Schema(description = "状态（0在线 1离线）")
    private String status;

    @Schema(description = "服务所在区域")
    private String zone;

    @Schema(description = "最后刷新时间")
    private Date lastRefreshTime;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;
}