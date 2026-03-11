package com.cq.panel.admin.server.web.controller.rc;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.RcAccessToken;
import com.cq.panel.admin.server.repository.service.IRcAccessTokenService;
import com.cq.panel.admin.server.web.domain.dto.rc.RcAccessTokenDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcAccessTokenQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.rc.RcAccessTokenVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.rc.RcAccessTokenConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * AccessToken管理 Controller
 * 
 * @author cq
 */
@Tag(name = "AccessToken管理", description = "AccessToken管理相关接口")
@RestController
@RequestMapping("/rc/accessToken")
public class RcAccessTokenController extends BaseController
{
    @Autowired
    private IRcAccessTokenService rcAccessTokenService;

    @Autowired
    private RcAccessTokenConverter rcAccessTokenConverter;

    /**
     * 查询AccessToken管理列表
     */
    @Operation(summary = "查询AccessToken管理列表", description = "根据条件分页获取AccessToken管理列表")
    @PreAuthorize("@ss.hasPermi('rc:accessToken:list')")
    @GetMapping("/list")
    public Result<PageVO<RcAccessTokenVO>> list(@Parameter(description = "查询参数") RcAccessTokenQueryDTO query)
    {
        startPage();
        RcAccessToken rcAccessToken = rcAccessTokenConverter.toEntity(query);
        List<RcAccessToken> list = rcAccessTokenService.selectRcAccessTokenList(rcAccessToken);
        List<RcAccessTokenVO> voList = rcAccessTokenConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo(list).getTotal()));
    }

    /**
     * 导出AccessToken管理列表
     */
    @Operation(summary = "导出AccessToken管理列表", description = "导出符合条件的AccessToken管理数据")
    @Log(title = "AccessToken管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('rc:accessToken:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") RcAccessTokenQueryDTO query)
    {
        RcAccessToken rcAccessToken = rcAccessTokenConverter.toEntity(query);
        List<RcAccessToken> list = rcAccessTokenService.selectRcAccessTokenList(rcAccessToken);
        ExcelUtil<RcAccessToken> util = new ExcelUtil<RcAccessToken>(RcAccessToken.class);
        util.exportExcel(response, list, "AccessToken管理数据");
    }

    /**
     * 获取AccessToken管理详细信息
     */
    @Operation(summary = "获取AccessToken管理详细信息", description = "根据ID获取AccessToken管理详细信息")
    @PreAuthorize("@ss.hasPermi('rc:accessToken:query')")
    @GetMapping(value = "/{id}")
    public Result<RcAccessTokenVO> getInfo(@Parameter(description = "ID", required = true) @PathVariable("id") Long id)
    {
        return Result.success(rcAccessTokenConverter.toVO(rcAccessTokenService.selectRcAccessTokenById(id)));
    }

    /**
     * 新增AccessToken管理
     */
    @Operation(summary = "新增AccessToken管理", description = "新增AccessToken管理信息")
    @PreAuthorize("@ss.hasPermi('rc:accessToken:add')")
    @Log(title = "AccessToken管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody RcAccessTokenDTO dto)
    {
        RcAccessToken rcAccessToken = rcAccessTokenConverter.toEntity(dto);
        if (!rcAccessTokenService.checkTokenValueUnique(rcAccessToken))
        {
            return Result.error("新增Token'" + rcAccessToken.getTokenValue() + "'失败，Token值已存在");
        }
        rcAccessToken.setCreateBy(getUsername());
        rcAccessTokenService.insertRcAccessToken(rcAccessToken);
        return Result.success();
    }

    /**
     * 修改AccessToken管理
     */
    @Operation(summary = "修改AccessToken管理", description = "修改AccessToken管理信息")
    @PreAuthorize("@ss.hasPermi('rc:accessToken:edit')")
    @Log(title = "AccessToken管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody RcAccessTokenDTO dto)
    {
        RcAccessToken rcAccessToken = rcAccessTokenConverter.toEntity(dto);
        if (!rcAccessTokenService.checkTokenValueUnique(rcAccessToken))
        {
            return Result.error("修改Token'" + rcAccessToken.getTokenValue() + "'失败，Token值已存在");
        }
        rcAccessToken.setUpdateBy(getUsername());
        rcAccessTokenService.updateRcAccessToken(rcAccessToken);
        return Result.success();
    }

    /**
     * 删除AccessToken管理
     */
    @Operation(summary = "删除AccessToken管理", description = "批量删除AccessToken管理")
    @PreAuthorize("@ss.hasPermi('rc:accessToken:remove')")
    @Log(title = "AccessToken管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@Parameter(description = "ID数组", required = true) @PathVariable Long[] ids)
    {
        rcAccessTokenService.deleteRcAccessTokenByIds(ids);
        return Result.success();
    }

    /**
     * 状态修改
     */
    @Operation(summary = "修改AccessToken状态", description = "修改AccessToken启用/禁用状态")
    @PreAuthorize("@ss.hasPermi('rc:accessToken:edit')")
    @Log(title = "AccessToken管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public Result<Void> changeStatus(@RequestBody RcAccessTokenDTO dto)
    {
        RcAccessToken rcAccessToken = new RcAccessToken();
        rcAccessToken.setId(dto.getId());
        rcAccessToken.setStatus(dto.getStatus());
        rcAccessToken.setUpdateBy(getUsername());
        rcAccessTokenService.updateStatus(rcAccessToken);
        return Result.success();
    }
}
