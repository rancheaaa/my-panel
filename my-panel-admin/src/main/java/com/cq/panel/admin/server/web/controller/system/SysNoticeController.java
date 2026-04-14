package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.SysNotice;
import com.cq.panel.admin.server.repository.service.ISysNoticeService;
import com.cq.panel.admin.server.web.domain.dto.system.SysNoticeDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysNoticeQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.SysNoticeVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.system.SysNoticeConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 公告 信息操作处理
 * 
 * @author cq
 */
@Tag(name = "通知公告", description = "通知公告相关接口")
@RestController
@RequestMapping("/system/notice")
public class SysNoticeController extends BaseController
{
    private final ISysNoticeService noticeService;

    private final SysNoticeConverter noticeConverter;

    public SysNoticeController(ISysNoticeService noticeService, SysNoticeConverter noticeConverter) {
        this.noticeService = noticeService;
        this.noticeConverter = noticeConverter;
    }

    /**
     * 获取通知公告列表
     */
    @Operation(summary = "获取通知公告列表", description = "根据条件分页获取通知公告列表")
    @RequirePermission("system:notice:list")
    @GetMapping("/list")
    public Result<PageVO<SysNoticeVO>> list(@Parameter(description = "查询参数") SysNoticeQueryDTO query)
    {
        startPage();
        SysNotice notice = noticeConverter.toEntity(query);
        List<SysNotice> list = noticeService.selectNoticeList(notice);
        List<SysNoticeVO> voList = noticeConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    /**
     * 根据通知公告编号获取详细信息
     */
    @Operation(summary = "根据通知公告编号获取详细信息", description = "根据公告ID获取公告详细信息")
    @RequirePermission("system:notice:query")
    @GetMapping(value = "/{noticeId}")
    public Result<SysNoticeVO> getInfo(@Parameter(description = "公告ID", required = true) @PathVariable Long noticeId)
    {
        return Result.success(noticeConverter.toVO(noticeService.selectNoticeById(noticeId)));
    }

    /**
     * 新增通知公告
     */
    @Operation(summary = "新增通知公告", description = "新增通知公告信息")
    @RequirePermission("system:notice:add")
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysNoticeDTO dto)
    {
        SysNotice notice = noticeConverter.toEntity(dto);
        notice.setCreateBy(getUsername());
        noticeService.insertNotice(notice);
        return Result.success();
    }

    /**
     * 修改通知公告
     */
    @Operation(summary = "修改通知公告", description = "修改通知公告信息")
    @RequirePermission("system:notice:edit")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysNoticeDTO dto)
    {
        SysNotice notice = noticeConverter.toEntity(dto);
        notice.setUpdateBy(getUsername());
        noticeService.updateNotice(notice);
        return Result.success();
    }

    /**
     * 删除通知公告
     */
    @Operation(summary = "删除通知公告", description = "批量删除通知公告")
    @RequirePermission("system:notice:remove")
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public Result<Void> remove(@Parameter(description = "公告ID数组", required = true) @PathVariable Long[] noticeIds)
    {
        noticeService.deleteNoticeByIds(noticeIds);
        return Result.success();
    }
}
