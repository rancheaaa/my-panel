package com.cq.panel.admin.server.web.domain.dto.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 节点分组 DTO
 * 
 * @author cq
 */
@Data
public class ArchNodeGroupDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long diagramId;
    private String groupName;
    private String groupColor;
    private String groupIcon;
    private Integer sortOrder;
    private String isExpanded;
    private String delFlag;
    private String remark;
}