package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "内置方法验证结果")
public class MethodValidationVO {

    @Schema(description = "验证是否通过")
    private Boolean valid;

    @Schema(description = "方法信息")
    private MethodInfoVO methodInfo;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "参数验证结果列表")
    private List<ParameterValidationResult> parameterResults;

    public MethodValidationVO() {
    }

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public MethodInfoVO getMethodInfo() {
        return methodInfo;
    }

    public void setMethodInfo(MethodInfoVO methodInfo) {
        this.methodInfo = methodInfo;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ParameterValidationResult> getParameterResults() {
        return parameterResults;
    }

    public void setParameterResults(List<ParameterValidationResult> parameterResults) {
        this.parameterResults = parameterResults;
    }

    @Schema(description = "参数验证结果")
    public static class ParameterValidationResult {
        @Schema(description = "参数索引")
        private Integer index;

        @Schema(description = "参数名称")
        private String name;

        @Schema(description = "参数类型")
        private String type;

        @Schema(description = "提供的值")
        private String providedValue;

        @Schema(description = "验证是否通过")
        private Boolean valid;

        @Schema(description = "错误信息")
        private String errorMessage;

        public ParameterValidationResult() {
        }

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
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

        public String getProvidedValue() {
            return providedValue;
        }

        public void setProvidedValue(String providedValue) {
            this.providedValue = providedValue;
        }

        public Boolean getValid() {
            return valid;
        }

        public void setValid(Boolean valid) {
            this.valid = valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}