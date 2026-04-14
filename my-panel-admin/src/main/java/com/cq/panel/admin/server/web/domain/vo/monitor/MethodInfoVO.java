package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "内置方法信息")
public class MethodInfoVO {

    @Schema(description = "方法全限定名")
    private String methodName;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "组件名称")
    private String componentName;

    @Schema(description = "方法描述")
    private String description;

    @Schema(description = "参数列表")
    private List<ParameterInfo> parameters;

    @Schema(description = "是否有参数")
    private Boolean hasParameters;

    public MethodInfoVO() {
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterInfo> parameters) {
        this.parameters = parameters;
    }

    public Boolean getHasParameters() {
        return hasParameters;
    }

    public void setHasParameters(Boolean hasParameters) {
        this.hasParameters = hasParameters;
    }

    @Schema(description = "参数信息")
    public static class ParameterInfo {
        @Schema(description = "参数名称")
        private String name;

        @Schema(description = "参数类型")
        private String type;

        @Schema(description = "参数类型显示名称")
        private String typeDisplayName;

        @Schema(description = "是否为基本类型")
        private Boolean isPrimitive;

        public ParameterInfo() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTypeDisplayName() {
            return typeDisplayName;
        }

        public void setTypeDisplayName(String typeDisplayName) {
            this.typeDisplayName = typeDisplayName;
        }

        public Boolean getIsPrimitive() {
            return isPrimitive;
        }

        public void setIsPrimitive(Boolean isPrimitive) {
            this.isPrimitive = isPrimitive;
        }
    }
}