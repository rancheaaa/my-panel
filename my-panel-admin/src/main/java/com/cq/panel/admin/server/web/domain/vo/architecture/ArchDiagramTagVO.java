package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图标签 VO
 * 
 * @author cq
 */
@Data
public class ArchDiagramTagVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String tagName;
    private String tagColor;
    private String tagType;
    private Integer useCount;
    private String createBy;
    private String createTime;
    private String delFlag;
}