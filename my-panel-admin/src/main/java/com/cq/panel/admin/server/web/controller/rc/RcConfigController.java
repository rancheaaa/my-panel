package com.cq.panel.admin.server.web.controller.rc;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.RcConfig;
import com.cq.panel.admin.server.repository.service.IRcConfigService;
import com.cq.panel.admin.server.repository.service.IRcConfigService.ExportResult;
import com.cq.panel.admin.server.web.domain.dto.rc.RcConfigDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcConfigImportDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcConfigQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.rc.RcConfigVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.rc.RcConfigConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.cq.panel.admin.server.common.utils.StringUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * 配置中心 Controller
 * 
 * @author cq
 */
@Tag(name = "配置中心", description = "配置中心相关接口")
@RestController
@RequestMapping("/rc/config")
public class RcConfigController extends BaseController
{
    private final IRcConfigService rcConfigService;

    private final RcConfigConverter rcConfigConverter;

    public RcConfigController(IRcConfigService rcConfigService, RcConfigConverter rcConfigConverter) {
        this.rcConfigService = rcConfigService;
        this.rcConfigConverter = rcConfigConverter;
    }

    /**
     * 查询配置中心列表
     */
    @Operation(summary = "查询配置中心列表", description = "根据条件分页获取配置中心列表")
    @RequirePermission("rc:configCenter:list")
    @GetMapping("/list")
    public Result<PageVO<RcConfigVO>> list(@Parameter(description = "查询参数") RcConfigQueryDTO query)
    {
        startPage();
        RcConfig rcConfig = rcConfigConverter.toEntity(query);
        List<RcConfig> list = rcConfigService.selectRcConfigList(rcConfig);
        List<RcConfigVO> voList = rcConfigConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    /**
     * 导出配置中心列表
     */
    @Operation(summary = "导出配置中心列表", description = "导出符合条件的配置中心数据")
    @Log(title = "配置中心", businessType = BusinessType.EXPORT)
    @RequirePermission("rc:configCenter:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") RcConfigQueryDTO query) throws IOException
    {
        RcConfig rcConfig = rcConfigConverter.toEntity(query);
        List<RcConfig> list = rcConfigService.selectRcConfigList(rcConfig);
        
        ExportResult result = rcConfigService.exportConfigs(list, query.getExportFormat(), query.getEnvId(), query.getProjectId());
        
        if (result.isExcel())
        {
            ExcelUtil<RcConfig> util = new ExcelUtil<>(RcConfig.class);
            util.exportExcel(response, list, result.getFileName());
            return;
        }

        response.setContentType(result.getContentType());
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + result.getFileName() + "." + result.getExtension());
        try (PrintWriter writer = response.getWriter())
        {
            writer.write(result.getContent());
            writer.flush();
        }
    }

    /**
     * 预览配置内容
     */
    @Operation(summary = "预览配置内容", description = "预览符合条件的配置数据内容")
    @RequirePermission("rc:configCenter:export")
    @GetMapping("/preview")
    public Result<String> preview(@RequestParam Long envId, @RequestParam Long projectId, @RequestParam(required = false) String exportFormat)
    {
        logger.info("Preview Request: envId={}, projectId={}, format={}", envId, projectId, exportFormat);
        RcConfig rcConfig = new RcConfig();
        rcConfig.setEnvId(envId);
        rcConfig.setProjectId(projectId);
        List<RcConfig> list = rcConfigService.selectRcConfigList(rcConfig);
        logger.info("Found {} configs for preview", list.size());
        
        if (list.isEmpty()) {
            return Result.success("未找到配置数据 (envId=" + envId + ", projectId=" + projectId + ")", "");
        }
        
        String format = exportFormat;
        if (StringUtils.isEmpty(format)) {
            format = "yml";
        }
        
        ExportResult result = rcConfigService.exportConfigs(list, format, envId, projectId);
        String content = result.getContent();
        logger.info("Export result content length: {}", (content != null ? content.length() : "null"));
        if (content != null && !content.isEmpty()) {
            logger.debug("Export result content preview: {}", content.substring(0, Math.min(content.length(), 100)));
        }
        return Result.success("查询成功", content);
    }

    /**
     * 获取配置中心详细信息
     */
    @Operation(summary = "获取配置中心详细信息", description = "根据配置ID获取配置中心详细信息")
    @RequirePermission("rc:configCenter:query")
    @GetMapping(value = "/{id}")
    public Result<RcConfigVO> getInfo(@Parameter(description = "配置ID", required = true) @PathVariable("id") Long id)
    {
        return Result.success(rcConfigConverter.toVO(rcConfigService.selectRcConfigById(id)));
    }

    /**
     * 新增配置中心
     */
    @Operation(summary = "新增配置中心", description = "新增配置中心信息")
    @RequirePermission("rc:configCenter:add")
    @Log(title = "配置中心", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody RcConfigDTO dto)
    {
        RcConfig rcConfig = rcConfigConverter.toEntity(dto);
        if (!rcConfigService.checkConfigKeyUnique(rcConfig))
        {
            return Result.error("新增配置键'" + rcConfig.getConfigKey() + "'失败，该项目在该环境下的配置键已存在");
        }
        rcConfig.setSource("0"); // 0: 手工新增
        rcConfig.setCreateBy(getUsername());
        rcConfigService.insertRcConfig(rcConfig);
        return Result.success();
    }

    /**
     * 修改配置中心
     */
    @Operation(summary = "修改配置中心", description = "修改配置中心信息")
    @RequirePermission("rc:configCenter:edit")
    @Log(title = "配置中心", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody RcConfigDTO dto)
    {
        RcConfig rcConfig = rcConfigConverter.toEntity(dto);
        if (!rcConfigService.checkConfigKeyUnique(rcConfig))
        {
            return Result.error("修改配置键'" + rcConfig.getConfigKey() + "'失败，该项目在该环境下的配置键已存在");
        }
        rcConfig.setUpdateBy(getUsername());
        rcConfigService.updateRcConfig(rcConfig);
        return Result.success();
    }

    /**
     * 删除配置中心
     */
    @Operation(summary = "删除配置中心", description = "批量删除配置中心")
    @RequirePermission("rc:configCenter:remove")
    @Log(title = "配置中心", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@Parameter(description = "配置ID数组", required = true) @PathVariable Long[] ids)
    {
        rcConfigService.deleteRcConfigByIds(ids);
        return Result.success();
    }

    /**
     * 批量导入配置
     */
    @Operation(summary = "批量导入配置", description = "批量导入 .properties 或 .yml/.yaml 配置文件内容")
    @RequirePermission("rc:configCenter:import")
    @Log(title = "配置中心", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public Result<Void> importConfig(@Validated @RequestBody RcConfigImportDTO dto)
    {
        rcConfigService.importConfig(dto.getEnvId(), dto.getProjectId(), dto.getContent(), dto.getFormat(), getUsername());
        return Result.success();
    }
}
