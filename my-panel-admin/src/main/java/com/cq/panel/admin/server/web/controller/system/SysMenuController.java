package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.model.TreeSelect;
import com.cq.panel.admin.server.repository.domain.SysMenu;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.repository.service.ISysMenuService;
import com.cq.panel.admin.server.web.domain.dto.system.SysMenuDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysMenuQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysMenuSortDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.SysMenuVO;
import com.cq.panel.admin.server.web.domain.vo.system.RoleMenuTreeSelectVO;
import com.cq.panel.admin.server.web.converter.system.SysMenuConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 菜单信息
 * 
 * @author cq
 */
@Tag(name = "菜单管理", description = "菜单管理相关接口")
@RestController
@RequestMapping("/system/menu")
public class SysMenuController extends BaseController
{
    private final ISysMenuService menuService;

    private final SysMenuConverter menuConverter;

    public SysMenuController(ISysMenuService menuService, SysMenuConverter menuConverter) {
        this.menuService = menuService;
        this.menuConverter = menuConverter;
    }

    /**
     * 获取菜单列表
     */
    @Operation(summary = "获取菜单列表", description = "根据条件获取菜单列表")
    @RequirePermission("system:menu:list")
    @GetMapping("/list")
    public Result<List<SysMenuVO>> list(@Parameter(description = "查询参数") SysMenuQueryDTO query)
    {
        SysMenu menu = menuConverter.toEntity(query);
        List<SysMenu> menus = menuService.selectMenuList(menu, getUserId());
        return Result.success(menuConverter.toVOList(menus));
    }

    /**
     * 根据菜单编号获取详细信息
     */
    @Operation(summary = "根据菜单编号获取详细信息", description = "根据菜单ID获取菜单详细信息")
    @RequirePermission("system:menu:query")
    @GetMapping(value = "/{menuId}")
    public Result<SysMenuVO> getInfo(@Parameter(description = "菜单ID", required = true) @PathVariable Long menuId)
    {
        return Result.success(menuConverter.toVO(menuService.selectMenuById(menuId)));
    }

    /**
     * 获取菜单下拉树列表
     */
    @Operation(summary = "获取菜单下拉树列表", description = "获取菜单下拉树列表")
    @GetMapping("/treeselect")
    public Result<List<TreeSelect>> treeselect(@Parameter(description = "查询参数") SysMenuQueryDTO query)
    {
        SysMenu menu = menuConverter.toEntity(query);
        List<SysMenu> menus = menuService.selectMenuList(menu, getUserId());
        return Result.success(menuService.buildMenuTreeSelect(menus));
    }

    /**
     * 加载对应角色菜单列表树
     */
    @Operation(summary = "加载对应角色菜单列表树", description = "根据角色ID加载对应角色菜单列表树")
    @GetMapping(value = "/roleMenuTreeselect/{roleId}")
    public Result<RoleMenuTreeSelectVO> roleMenuTreeselect(@Parameter(description = "角色ID", required = true) @PathVariable("roleId") Long roleId)
    {
        List<SysMenu> menus = menuService.selectMenuList(getUserId());
        RoleMenuTreeSelectVO vo = new RoleMenuTreeSelectVO();
        vo.setCheckedKeys(menuService.selectMenuListByRoleId(roleId));
        vo.setMenus(menuService.buildMenuTreeSelect(menus));
        return Result.success(vo);
    }

    /**
     * 新增菜单
     */
    @Operation(summary = "新增菜单", description = "新增菜单信息")
    @RequirePermission("system:menu:add")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysMenuDTO dto)
    {
        SysMenu menu = menuConverter.toEntity(dto);
        if (!menuService.checkMenuNameUnique(menu))
        {
            return Result.error("新增菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        else if (UserConstants.YES_FRAME.equals(menu.getIsFrame()) && !MyStringUtils.isHttp(menu.getPath()))
        {
            return Result.error("新增菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        }
        menu.setCreateBy(getUsername());
        menuService.insertMenu(menu);
        return Result.success();
    }

    /**
     * 修改菜单
     */
    @Operation(summary = "修改菜单", description = "修改菜单信息")
    @RequirePermission("system:menu:edit")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysMenuDTO dto)
    {
        SysMenu menu = menuConverter.toEntity(dto);
        if (!menuService.checkMenuNameUnique(menu))
        {
            return Result.error("修改菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        else if (UserConstants.YES_FRAME.equals(menu.getIsFrame()) && !MyStringUtils.isHttp(menu.getPath()))
        {
            return Result.error("修改菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        }
        else if (menu.getMenuId().equals(menu.getParentId()))
        {
            return Result.error("修改菜单'" + menu.getMenuName() + "'失败，上级菜单不能选择自己");
        }
        menu.setUpdateBy(getUsername());
        menuService.updateMenu(menu);
        return Result.success();
    }

    /**
     * 菜单排序
     */
    @Operation(summary = "菜单排序", description = "批量修改菜单排序")
    @RequirePermission("system:menu:edit")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @PutMapping("/sort")
    public Result<Void> sort(@Validated @RequestBody List<SysMenuSortDTO> sortList)
    {
        for (SysMenuSortDTO sort : sortList)
        {
            SysMenu menu = new SysMenu();
            menu.setMenuId(sort.getMenuId());
            menu.setOrderNum(sort.getOrderNum());
            menu.setUpdateBy(getUsername());
            menuService.updateMenu(menu);
        }
        return Result.success();
    }

    /**
     * 删除菜单
     */
    @Operation(summary = "删除菜单", description = "删除菜单")
    @RequirePermission("system:menu:remove")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{menuId}")
    public Result<Void> remove(@Parameter(description = "菜单ID", required = true) @PathVariable("menuId") Long menuId)
    {
        if (menuService.hasChildByMenuId(menuId))
        {
            return Result.error("存在子菜单,不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId))
        {
            return Result.error("菜单已分配,不允许删除");
        }
        menuService.deleteMenuById(menuId);
        return Result.success();
    }
}
