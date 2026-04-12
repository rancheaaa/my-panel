package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.repository.service.IArchDiagramVersionService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 架构图版本管理Controller
 *
 * @author
 */
@Slf4j
@RestController
@RequestMapping("/api/arch/version")
@RequiredArgsConstructor
@Tag(name = "架构图版本管理", description = "架构图版本管理接口")
public class ArchDiagramVersionController extends BaseController {

    private final IArchDiagramVersionService archDiagramVersionService;

    /**
     * 创建版本快照
     */
    @Operation(summary = "创建版本快照", description = "为架构图创建版本快照")
    @PostMapping("/create")
    public Result<Map<String, Object>> createSnapshot(
            @Parameter(description = "架构图ID", required = true) @RequestParam Long diagramId,
            @Parameter(description = "版本号", required = true) @RequestParam String version,
            @Parameter(description = "版本名称") @RequestParam(required = false) String versionName,
            @Parameter(description = "变更摘要") @RequestParam(required = false) String changeSummary) {
        try {
            Long versionId = archDiagramVersionService.createVersionSnapshot(diagramId, version, versionName,
                    changeSummary);
            log.info("版本快照创建成功: versionId={}, diagramId={}, version={}", versionId, diagramId, version);
            return Result.success("版本快照创建成功", Map.of("versionId", versionId, "version", version));
        } catch (Exception e) {
            log.error("创建版本快照失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询版本历史
     */
    @Operation(summary = "查询版本历史", description = "查询架构图的版本历史")
    @GetMapping("/list/{diagramId}")
    public Result<Object> listHistoryByDiagramId(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        try {
            log.info("查询架构图版本历史: diagramId={}", diagramId);
            return Result.success("查询成功", archDiagramVersionService.getVersionHistory(diagramId));
        } catch (Exception e) {
            log.error("查询版本历史失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 回滚到指定版本
     */
    @Operation(summary = "回滚到指定版本", description = "将架构图回滚到指定版本")
    @PostMapping("/restore/{versionId}")
    public Result<Map<String, Object>> restoreVersion(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        try {
            Long diagramId = archDiagramVersionService.restoreVersion(versionId);
            log.info("版本恢复成功: versionId={}, diagramId={}", versionId, diagramId);
            return Result.success("版本恢复成功", Map.of("diagramId", diagramId));
        } catch (Exception e) {
            log.error("恢复版本失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取版本详细信息
     */
    @Operation(summary = "获取版本详细信息", description = "获取版本的详细信息和数据")
    @GetMapping("/detail/{versionId}")
    public Result<Object> getVersionDetail(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        try {
            log.info("获取版本详细信息: versionId={}", versionId);
            return Result.success("查询成功", archDiagramVersionService.getVersionDetail(versionId));
        } catch (Exception e) {
            log.error("获取版本详情失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除版本
     */
    @Operation(summary = "删除版本", description = "删除指定的版本")
    @DeleteMapping("/delete/{versionId}")
    public Result<Void> deleteVersion(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        try {
            boolean result = archDiagramVersionService.deleteVersion(versionId);
            if (result) {
                log.info("版本删除成功: versionId={}", versionId);
                return Result.success("版本删除成功");
            } else {
                return Result.error("版本删除失败");
            }
        } catch (Exception e) {
            log.error("删除版本失败", e);
            return Result.error(e.getMessage());
        }
    }
}