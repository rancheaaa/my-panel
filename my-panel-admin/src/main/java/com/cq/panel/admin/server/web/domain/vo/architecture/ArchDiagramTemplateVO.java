package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图模板 VO
 * 
 * @author cq
 */
@Data
public class ArchDiagramTemplateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String templateName;
    private String templateDescription;
    private String category;
    private String thumbnail;
    private String templateData;
    private String isPublic;
    private String status;
    private Integer useCount;
    private Double rating;
    private String tags;
    private String createBy;
    private String createTime;
    private String updateBy;
    private String updateTime;
    private String delFlag;
    private String remark;
}