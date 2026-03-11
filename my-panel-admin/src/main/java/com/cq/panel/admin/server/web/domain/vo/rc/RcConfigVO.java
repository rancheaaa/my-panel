package com.cq.panel.admin.server.web.domain.vo.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * 配置中心 VO
 * 
 * @author cq
 */
@Data
@Schema(description = "配置中心VO")
public class RcConfigVO {
    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "环境ID")
    private Long envId;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "环境名称")
    private String envName;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "配置描述")
    private String configDesc;

    @Schema(description = "来源（0手工新增 1批量导入）")
    private String source;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;
}
