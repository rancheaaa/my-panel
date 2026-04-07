package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "内置方法验证请求")
public class MethodValidationDTO {

    @NotBlank(message = "方法名称不能为空")
    @Schema(description = "方法全限定名", required = true)
    private String methodName;

    @Schema(description = "参数值列表（JSON数组格式）")
    private String parameterValues;

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getParameterValues() {
        return parameterValues;
    }

    public void setParameterValues(String parameterValues) {
        this.parameterValues = parameterValues;
    }
}