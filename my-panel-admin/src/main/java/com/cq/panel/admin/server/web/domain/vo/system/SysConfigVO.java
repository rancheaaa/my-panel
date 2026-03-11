package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "参数配置视图对象")
public record SysConfigVO(
    @Schema(description = "参数主键", example = "1")
    Long configId,

    @Schema(description = "参数名称", example = "主框架页-侧边栏主题")
    String configName,

    @Schema(description = "参数键名", example = "sys.index.sideTheme")
    String configKey,

    @Schema(description = "参数键值", example = "theme-dark")
    String configValue,

    @Schema(description = "系统内置（Y是 N否）", example = "Y")
    String configType,

    @Schema(description = "备注", example = "深色主题")
    String remark,

    @Schema(description = "创建者")
    String createBy,
    
    @Schema(description = "创建时间")
    Date createTime,
    
    @Schema(description = "更新者")
    String updateBy,
    
    @Schema(description = "更新时间")
    Date updateTime
) {}
