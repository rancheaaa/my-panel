package com.cq.panel.admin.server.web.domain.dto.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图标签 DTO
 * 
 * @author cq
 */
@Data
public class ArchDiagramTagDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String tagName;
    private String tagColor;
    private String tagType;
    private String tagDefaultValue;
    private String delFlag;
}