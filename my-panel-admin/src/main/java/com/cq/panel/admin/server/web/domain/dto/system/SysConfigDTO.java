package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "参数配置添加/修改对象")
public record SysConfigDTO(
    @Schema(description = "参数主键", example = "1")
    Long configId,

    @Schema(description = "参数名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "主框架页-侧边栏主题")
    @NotBlank(message = "参数名称不能为空")
    @Size(min = 0, max = 100, message = "参数名称不能超过100个字符")
    String configName,

    @Schema(description = "参数键名", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys.index.sideTheme")
    @NotBlank(message = "参数键名长度不能为空")
    @Size(min = 0, max = 100, message = "参数键名长度不能超过100个字符")
    String configKey,

    @Schema(description = "参数键值", requiredMode = Schema.RequiredMode.REQUIRED, example = "theme-dark")
    @NotBlank(message = "参数键值不能为空")
    @Size(min = 0, max = 500, message = "参数键值长度不能超过500个字符")
    String configValue,

    @Schema(description = "系统内置（Y是 N否）", example = "Y")
    String configType,
    
    @Schema(description = "备注", example = "深色主题")
    String remark
) {}
