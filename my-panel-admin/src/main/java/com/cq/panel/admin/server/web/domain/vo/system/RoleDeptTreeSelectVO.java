package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cq.panel.admin.server.web.domain.model.TreeSelect;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "角色部门树选择视图对象")
public class RoleDeptTreeSelectVO {
    @Schema(description = "选中的部门ID列表")
    private List<Long> checkedKeys;

    @Schema(description = "部门树列表")
    private List<TreeSelect> depts;

    public void setCheckedKeys(List<Long> checkedKeys) {
        this.checkedKeys = checkedKeys;
    }

    public void setDepts(List<TreeSelect> depts) {
        this.depts = depts;
    }
}
