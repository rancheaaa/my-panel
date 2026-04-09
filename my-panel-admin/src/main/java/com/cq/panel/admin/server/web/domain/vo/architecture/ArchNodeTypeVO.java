package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 节点类型 VO
 * 
 * @author cq
 */
@Data
public class ArchNodeTypeVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String typeCode;
    private String typeName;
    private String icon;
    private String category;
    private Integer defaultWidth;
    private Integer defaultHeight;
    private String defaultStyle;
    private String defaultProperties;
    private String validationRules;
    private String isSystem;
    private String isActive;
    private Integer sortOrder;
    private String createBy;
    private String createTime;
    private String updateBy;
    private String updateTime;
    private String delFlag;
    private String remark;
}