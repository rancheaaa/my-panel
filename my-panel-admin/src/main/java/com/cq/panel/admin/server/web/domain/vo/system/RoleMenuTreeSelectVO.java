package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cq.panel.admin.server.web.domain.model.TreeSelect;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "角色菜单树选择视图对象")
public class RoleMenuTreeSelectVO {
    @Schema(description = "选中的菜单ID列表")
    private List<Long> checkedKeys;

    @Schema(description = "菜单树列表")
    private List<TreeSelect> menus;

    public void setCheckedKeys(List<Long> checkedKeys) {
        this.checkedKeys = checkedKeys;
    }

    public void setMenus(List<TreeSelect> menus) {
        this.menus = menus;
    }
}
