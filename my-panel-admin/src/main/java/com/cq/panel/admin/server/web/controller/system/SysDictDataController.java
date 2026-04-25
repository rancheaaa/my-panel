package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.web.domain.dto.system.SysDictDataDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysDictDataQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.system.SysDictDataVO;
import com.cq.panel.admin.server.web.converter.system.SysDictDataConverter;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.SysDictData;
import com.cq.panel.admin.server.repository.service.ISysDictDataService;
import com.cq.panel.admin.server.repository.service.ISysDictTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据字典信息
 * 
 * @author cq
 */
@Tag(name = "字典数据", description = "字典数据相关接口")
@RestController
@RequestMapping("/system/dict/data")
public class SysDictDataController extends BaseController
{
    private final ISysDictDataService dictDataService;

    private final ISysDictTypeService dictTypeService;

    private final SysDictDataConverter dictDataConverter;

    public SysDictDataController(ISysDictDataService dictDataService, ISysDictTypeService dictTypeService, SysDictDataConverter dictDataConverter) {
        this.dictDataService = dictDataService;
        this.dictTypeService = dictTypeService;
        this.dictDataConverter = dictDataConverter;
    }

    @Operation(summary = "查询字典数据列表", description = "根据条件分页获取字典数据列表")
    @RequirePermission("system:dict:list")
    @GetMapping("/list")
    public Result<PageVO<SysDictDataVO>> list(@Parameter(description = "查询参数") SysDictDataQueryDTO query)
    {
        startPage();
        SysDictData dictData = dictDataConverter.toEntity(query);
        List<SysDictData> list = dictDataService.selectDictDataList(dictData);
        PageInfo<SysDictData> pageInfo = new PageInfo<>(list);
        List<SysDictDataVO> voList = dictDataConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, pageInfo.getTotal()));
    }

    @Operation(summary = "导出字典数据", description = "导出符合条件的字典数据")
    @Log(title = "字典数据", businessType = BusinessType.EXPORT)
    @RequirePermission("system:dict:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") SysDictDataQueryDTO query)
    {
        SysDictData dictData = dictDataConverter.toEntity(query);
        List<SysDictData> list = dictDataService.selectDictDataList(dictData);
        ExcelUtil<SysDictData> util = new ExcelUtil<>(SysDictData.class);
        util.exportExcel(response, list, "字典数据");
    }

    /**
     * 查询字典数据详细
     */
    @Operation(summary = "查询字典数据详细", description = "根据字典编码获取字典数据详细信息")
    @RequirePermission("system:dict:query")
    @GetMapping(value = "/{dictCode}")
    public Result<SysDictDataVO> getInfo(@Parameter(description = "字典编码", required = true) @PathVariable Long dictCode)
    {
        return Result.success(dictDataConverter.toVO(dictDataService.selectDictDataById(dictCode)));
    }

    /**
     * 根据字典类型查询字典数据信息
     */
    @Operation(summary = "根据字典类型查询字典数据信息", description = "根据字典类型获取字典数据列表（无需分页）")
    @GetMapping(value = "/type/{dictType}")
    public Result<List<SysDictDataVO>> dictType(@Parameter(description = "字典类型", required = true) @PathVariable String dictType)
    {
        List<SysDictData> data = dictTypeService.selectDictDataByType(dictType);
        if (StringUtils.isNull(data))
        {
            data = new ArrayList<>();
        }
        return Result.success(dictDataConverter.toVOList(data));
    }

    /**
     * 新增字典类型
     */
    @Operation(summary = "新增字典数据", description = "新增字典数据信息")
    @RequirePermission("system:dict:add")
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysDictDataDTO dto)
    {
        SysDictData dict = dictDataConverter.toEntity(dto);
        dict.setCreateBy(getUsername());
        dictDataService.insertDictData(dict);
        return Result.success();
    }

    /**
     * 修改保存字典类型
     */
    @Operation(summary = "修改字典数据", description = "修改字典数据信息")
    @RequirePermission("system:dict:edit")
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysDictDataDTO dto)
    {
        SysDictData dict = dictDataConverter.toEntity(dto);
        dict.setUpdateBy(getUsername());
        dictDataService.updateDictData(dict);
        return Result.success();
    }

    /**
     * 删除字典类型
     */
    @Operation(summary = "删除字典数据", description = "批量删除字典数据")
    @RequirePermission("system:dict:remove")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictCodes}")
    public Result<Void> remove(@Parameter(description = "字典编码数组", required = true) @PathVariable Long[] dictCodes)
    {
        dictDataService.deleteDictDataByIds(dictCodes);
        return Result.success();
    }
}
