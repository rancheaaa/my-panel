package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.web.domain.dto.system.SysDictTypeDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysDictTypeQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.system.SysDictTypeVO;
import com.cq.panel.admin.server.web.converter.system.SysDictTypeConverter;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.SysDictType;
import com.cq.panel.admin.server.repository.service.ISysDictTypeService;
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
 * 数据字典信息
 * 
 * @author cq
 */
@Tag(name = "字典类型", description = "字典类型相关接口")
@RestController
@RequestMapping("/system/dict/type")
public class SysDictTypeController extends BaseController
{
    @Autowired
    private ISysDictTypeService dictTypeService;

    @Autowired
    private SysDictTypeConverter dictTypeConverter;

    @Operation(summary = "查询字典类型列表", description = "根据条件分页获取字典类型列表")
    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    @GetMapping("/list")
    public Result<PageVO<SysDictTypeVO>> list(@Parameter(description = "查询参数") SysDictTypeQueryDTO query)
    {
        startPage();
        SysDictType dictType = dictTypeConverter.toEntity(query);
        List<SysDictType> list = dictTypeService.selectDictTypeList(dictType);
        List<SysDictTypeVO> voList = dictTypeConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo(list).getTotal()));
    }

    @Operation(summary = "导出字典类型", description = "导出符合条件的字典类型数据")
    @Log(title = "字典类型", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:dict:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") SysDictTypeQueryDTO query)
    {
        SysDictType dictType = dictTypeConverter.toEntity(query);
        List<SysDictType> list = dictTypeService.selectDictTypeList(dictType);
        ExcelUtil<SysDictType> util = new ExcelUtil<SysDictType>(SysDictType.class);
        util.exportExcel(response, list, "字典类型");
    }

    /**
     * 查询字典类型详细
     */
    @Operation(summary = "查询字典类型详细", description = "根据字典ID获取字典类型详细信息")
    @PreAuthorize("@ss.hasPermi('system:dict:query')")
    @GetMapping(value = "/{dictId}")
    public Result<SysDictTypeVO> getInfo(@Parameter(description = "字典ID", required = true) @PathVariable Long dictId)
    {
        return Result.success(dictTypeConverter.toVO(dictTypeService.selectDictTypeById(dictId)));
    }

    /**
     * 新增字典类型
     */
    @Operation(summary = "新增字典类型", description = "新增字典类型信息")
    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @Log(title = "字典类型", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysDictTypeDTO dto)
    {
        SysDictType dict = dictTypeConverter.toEntity(dto);
        if (!dictTypeService.checkDictTypeUnique(dict))
        {
            return Result.error("新增字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setCreateBy(getUsername());
        dictTypeService.insertDictType(dict);
        return Result.success();
    }

    /**
     * 修改字典类型
     */
    @Operation(summary = "修改字典类型", description = "修改字典类型信息")
    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @Log(title = "字典类型", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysDictTypeDTO dto)
    {
        SysDictType dict = dictTypeConverter.toEntity(dto);
        if (!dictTypeService.checkDictTypeUnique(dict))
        {
            return Result.error("修改字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setUpdateBy(getUsername());
        dictTypeService.updateDictType(dict);
        return Result.success();
    }

    /**
     * 删除字典类型
     */
    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @Operation(summary = "删除字典类型", description = "批量删除字典类型")
    @DeleteMapping("/{dictIds}")
    public Result<Void> remove(@Parameter(description = "字典ID数组", required = true) @PathVariable Long[] dictIds)
    {
        dictTypeService.deleteDictTypeByIds(dictIds);
        return Result.success();
    }

    /**
     * 刷新字典缓存
     */
    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @Log(title = "字典类型", businessType = BusinessType.CLEAN)
    @Operation(summary = "刷新字典缓存", description = "清除并重新加载所有字典缓存")
    @DeleteMapping("/refreshCache")
    public Result<Void> refreshCache()
    {
        dictTypeService.resetDictCache();
        return Result.success();
    }

    /**
     * 获取字典选择框列表
     */
    @Operation(summary = "获取字典选择框列表", description = "获取所有字典类型列表")
    @GetMapping("/optionselect")
    public Result<List<SysDictTypeVO>> optionselect()
    {
        List<SysDictType> dictTypes = dictTypeService.selectDictTypeAll();
        return Result.success(dictTypeConverter.toVOList(dictTypes));
    }
}
