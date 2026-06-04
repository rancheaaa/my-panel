package com.cq.panel.admin.server.web.domain.dto.batch;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BatchConfigExportDTO {
    @NotBlank(message = "源节点ID不能为空")
    private String sourceAgentId;
}
