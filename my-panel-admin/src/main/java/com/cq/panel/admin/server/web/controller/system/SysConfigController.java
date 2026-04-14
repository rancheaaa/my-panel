package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.SysConfig;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import com.cq.panel.admin.server.web.domain.dto.system.SysConfigDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysConfigQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.SysConfigVO;
import com.cq.panel.admin.server.web.domain.vo.system.ConfigValueVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.system.SysConfigConverter;
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
 * 参数配置 信息操作处理
 * 
 * @author cq
 */
@Tag(name = "参数配置", description = "参数配置相关接口")
@RestController
@RequestMapping("/system/config")
public class SysConfigController extends BaseController
{
    private final ISysConfigService configService;

    private final SysConfigConverter configConverter;

    public SysConfigController(ISysConfigService configService, SysConfigConverter configConverter) {
        this.configService = configService;
        this.configConverter = configConverter;
    }

    /**
     * 获取参数配置列表
     */
    @Operation(summary = "获取参数配置列表", description = "根据条件分页获取参数配置列表")
    @RequirePermission("system:config:list")
    @GetMapping("/list")
    public Result<PageVO<SysConfigVO>> list(@Parameter(description = "查询参数") SysConfigQueryDTO query)
    {
        startPage();
        SysConfig config = configConverter.toEntity(query);
        List<SysConfig> list = configService.selectConfigList(config);
        List<SysConfigVO> voList = configConverter.toVOList(list);
        
        return Result.success(new PageVO<>(voList, new PageInfo<>(voList).getTotal()));
    }

    @Operation(summary = "导出参数配置", description = "导出符合条件的参数配置数据")
    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @RequirePermission("system:config:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") SysConfigQueryDTO query)
    {
        SysConfig config = configConverter.toEntity(query);
        List<SysConfig> list = configService.selectConfigList(config);
        ExcelUtil<SysConfig> util = new ExcelUtil<>(SysConfig.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @Operation(summary = "根据参数编号获取详细信息", description = "根据参数ID获取参数详细信息")
    @RequirePermission("system:config:query")
    @GetMapping(value = "/{configId}")
    public Result<SysConfigVO> getInfo(@Parameter(description = "参数ID", required = true) @PathVariable Long configId)
    {
        return Result.success(configConverter.toVO(configService.selectConfigById(configId)));
    }

    /**
     * 根据参数键名查询参数值
     */
    @Operation(summary = "根据参数键名查询参数值", description = "根据参数键名获取参数值")
    @GetMapping(value = "/configKey/{configKey}")
    public Result<ConfigValueVO> getConfigKey(@Parameter(description = "参数键名", required = true) @PathVariable String configKey)
    {
        return Result.success(new ConfigValueVO(configService.selectConfigByKey(configKey)));
    }

    /**
     * 新增参数配置
     */
    @Operation(summary = "新增参数配置", description = "新增参数配置信息")
    @RequirePermission("system:config:add")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysConfigDTO dto)
    {
        SysConfig config = configConverter.toEntity(dto);
        if (!configService.checkConfigKeyUnique(config))
        {
            return Result.error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setCreateBy(getUsername());
        configService.insertConfig(config);
        return Result.success();
    }

    /**
     * 修改参数配置
     */
    @Operation(summary = "修改参数配置", description = "修改参数配置信息")
    @RequirePermission("system:config:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysConfigDTO dto)
    {
        SysConfig config = configConverter.toEntity(dto);
        if (!configService.checkConfigKeyUnique(config))
        {
            return Result.error("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setUpdateBy(getUsername());
        configService.updateConfig(config);
        return Result.success();
    }

    /**
     * 删除参数配置
     */
    @Operation(summary = "删除参数配置", description = "批量删除参数配置")
    @RequirePermission("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public Result<Void> remove(@Parameter(description = "参数ID数组", required = true) @PathVariable Long[] configIds)
    {
        configService.deleteConfigByIds(configIds);
        return Result.success();
    }

    /**
     * 刷新参数缓存
     */
    @Operation(summary = "刷新参数缓存", description = "清除并重新加载所有参数缓存")
    @RequirePermission("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public Result<Void> refreshCache()
    {
        configService.resetConfigCache();
        return Result.success();
    }
}
