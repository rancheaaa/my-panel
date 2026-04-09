package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 节点分组 VO
 * 
 * @author cq
 */
@Data
public class ArchNodeGroupVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long diagramId;
    private String groupName;
    private String groupColor;
    private String groupIcon;
    private Integer sortOrder;
    private String isExpanded;
    private String createBy;
    private String createTime;
    private String updateBy;
    private String updateTime;
    private String delFlag;
    private String remark;
}