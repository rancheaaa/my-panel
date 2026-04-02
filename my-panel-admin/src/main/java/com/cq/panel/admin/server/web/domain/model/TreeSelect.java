package com.cq.panel.admin.server.web.domain.model;

import com.cq.panel.admin.server.repository.domain.SysDept;
import com.cq.panel.admin.server.repository.domain.SysMenu;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tree select树结构实体类
 * 
 * @author cq
 */
@Data
public class TreeSelect implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 节点名称 */
    private String label;

    /** 子节点 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<TreeSelect> children;

    /**
     * 默认构造器
     */
    public TreeSelect() {
    }

    /**
     * 从SysDept构造TreeSelect
     * @param dept 部门对象
     */
    public TreeSelect(SysDept dept) {
        this.id = dept.getDeptId();
        this.label = dept.getDeptName();
        this.children = dept.getChildren().stream()
                .map(TreeSelect::new)
                .collect(Collectors.toList());
    }

    /**
     * 从SysMenu构造TreeSelect
     * @param menu 菜单对象
     */
    public TreeSelect(SysMenu menu) {
        this.id = menu.getMenuId();
        this.label = menu.getMenuName();
        this.children = menu.getChildren().stream()
                .map(TreeSelect::new)
                .collect(Collectors.toList());
    }
}