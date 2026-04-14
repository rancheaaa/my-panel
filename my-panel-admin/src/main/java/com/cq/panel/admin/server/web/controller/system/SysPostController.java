package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.SysPost;
import com.cq.panel.admin.server.repository.service.ISysPostService;
import com.cq.panel.admin.server.web.domain.dto.system.SysPostDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysPostQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysPostSortDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.SysPostVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.system.SysPostConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 岗位信息操作处理
 * 
 * @author cq
 */
@Tag(name = "岗位管理", description = "岗位管理相关接口")
@RestController
@RequestMapping("/system/post")
public class SysPostController extends BaseController
{
    private final ISysPostService postService;

    private final SysPostConverter postConverter;

    public SysPostController(ISysPostService postService, SysPostConverter postConverter) {
        this.postService = postService;
        this.postConverter = postConverter;
    }

    /**
     * 获取岗位列表
     */
    @Operation(summary = "获取岗位列表", description = "根据条件分页获取岗位列表")
    @RequirePermission("system:post:list")
    @GetMapping("/list")
    public Result<PageVO<SysPostVO>> list(@Parameter(description = "查询参数") SysPostQueryDTO query)
    {
        startPage();
        SysPost post = postConverter.toEntity(query);
        List<SysPost> list = postService.selectPostList(post);
        List<SysPostVO> voList = postConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }
    
    @Operation(summary = "导出岗位数据", description = "导出符合条件的岗位数据")
    @Log(title = "岗位管理", businessType = BusinessType.EXPORT)
    @RequirePermission("system:post:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") SysPostQueryDTO query)
    {
        SysPost post = postConverter.toEntity(query);
        List<SysPost> list = postService.selectPostList(post);
        ExcelUtil<SysPost> util = new ExcelUtil<>(SysPost.class);
        util.exportExcel(response, list, "岗位数据");
    }

    /**
     * 根据岗位编号获取详细信息
     */
    @Operation(summary = "根据岗位编号获取详细信息", description = "根据岗位ID获取岗位详细信息")
    @RequirePermission("system:post:query")
    @GetMapping(value = "/{postId}")
    public Result<SysPostVO> getInfo(@Parameter(description = "岗位ID", required = true) @PathVariable Long postId)
    {
        return Result.success(postConverter.toVO(postService.selectPostById(postId)));
    }

    /**
     * 新增岗位
     */
    @RequirePermission("system:post:add")
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    @Operation(summary = "新增岗位", description = "新增岗位信息")
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysPostDTO dto)
    {
        SysPost post = postConverter.toEntity(dto);
        if (!postService.checkPostNameUnique(post))
        {
            return Result.error("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        }
        else if (!postService.checkPostCodeUnique(post))
        {
            return Result.error("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        post.setCreateBy(getUsername());
        postService.insertPost(post);
        return Result.success();
    }

    /**
     * 修改岗位
     */
    @RequirePermission("system:post:edit")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改岗位", description = "修改岗位信息")
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysPostDTO dto)
    {
        SysPost post = postConverter.toEntity(dto);
        if (!postService.checkPostNameUnique(post))
        {
            return Result.error("修改岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        }
        else if (!postService.checkPostCodeUnique(post))
        {
            return Result.error("修改岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        post.setUpdateBy(getUsername());
        postService.updatePost(post);
        return Result.success();
    }

    /**
     * 岗位排序
     */
    @Operation(summary = "岗位排序", description = "批量修改岗位排序")
    @RequirePermission("system:post:edit")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @PostMapping("/sort")
    public Result<Void> sort(@Validated @RequestBody List<SysPostSortDTO> sortList)
    {
        for (SysPostSortDTO sort : sortList)
        {
            SysPost post = new SysPost();
            post.setPostId(sort.getPostId());
            post.setPostSort(sort.getPostSort());
            post.setUpdateBy(getUsername());
            postService.updatePost(post);
        }
        return Result.success();
    }

    /**
     * 删除岗位
     */
    @Operation(summary = "删除岗位", description = "批量删除岗位")
    @RequirePermission("system:post:remove")
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{postIds}")
    public Result<Void> remove(@Parameter(description = "岗位ID数组", required = true) @PathVariable Long[] postIds)
    {
        postService.deletePostByIds(postIds);
        return Result.success();
    }

    /**
     * 获取岗位选择框列表
     */
    @Operation(summary = "获取岗位选择框列表", description = "获取所有岗位列表")
    @GetMapping("/optionselect")
    public Result<List<SysPostVO>> optionselect()
    {
        List<SysPost> posts = postService.selectPostAll();
        return Result.success(postConverter.toVOList(posts));
    }
}