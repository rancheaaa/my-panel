package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.service.IBatchTaskImportService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskImportDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportBatchVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportPreviewVO;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "批量任务导入", description = "批量任务导入相关接口")
@RestController
@RequestMapping("/batch/task-import")
public class BatchTaskImportController extends BaseController {

    private static final String CHINESE_REGEX = "[\\u4e00-\\u9fa5]";

    @Autowired
    private IBatchTaskImportService importService;

    @PostMapping("/template")
    @Operation(summary = "下载导入模板")
    @RequirePermission("batch:task:import")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil<BatchTaskImportDTO> util = new ExcelUtil<>(BatchTaskImportDTO.class);
        List<BatchTaskImportDTO> examples = buildTemplateExamples();
        String fileName = "batch_task_import_template_" + new java.text.SimpleDateFormat("yyyyMMddHHmmssSSS").format(new java.util.Date()) + ".xlsx";
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            util.exportExcel(response, examples, "BatchTaskImport");
        } catch (Exception e) {
            throw new RuntimeException("下载模板失败: " + e.getMessage(), e);
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "上传批量任务Excel")
    @RequirePermission("batch:task:import")
    @Log(title = "批量任务导入", businessType = BusinessType.IMPORT)
    public Result<TaskImportPreviewVO> upload(@RequestParam("file") MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.matches(".*" + CHINESE_REGEX + ".*")) {
            return Result.error("文件名称不能包含中文字符");
        }
        return Result.success(importService.uploadAndValidate(file, getUsername()));
    }

    @GetMapping("/preview/{batchNo}")
    @Operation(summary = "预览临时表数据")
    @RequirePermission("batch:task:import")
    public Result<TaskImportPreviewVO> preview(
            @Parameter(description = "批次号", required = true) @PathVariable String batchNo) {
        return Result.success(importService.preview(batchNo));
    }

    @PostMapping("/commit/{batchNo}")
    @Operation(summary = "批导入（事务）")
    @RequirePermission("batch:task:import")
    @Log(title = "批量任务导入", businessType = BusinessType.IMPORT)
    public Result<Integer> commit(
            @Parameter(description = "批次号", required = true) @PathVariable String batchNo,
            @Parameter(description = "导入模式: STRICT-严格(默认)/LOOSE-松散") @RequestParam(defaultValue = "STRICT") String mode) {
        return Result.success(importService.commitImport(batchNo, mode));
    }

    @PostMapping("/rollback/{batchNo}")
    @Operation(summary = "批回退")
    @RequirePermission("batch:task:import")
    @Log(title = "批量任务导入", businessType = BusinessType.OTHER)
    public Result<Integer> rollback(
            @Parameter(description = "批次号", required = true) @PathVariable String batchNo) {
        return Result.success(importService.rollbackImport(batchNo));
    }

    @PutMapping("/batch-start/{batchNo}")
    @Operation(summary = "批启用")
    @RequirePermission("batch:task:import")
    public Result<Integer> batchStart(
            @Parameter(description = "批次号", required = true) @PathVariable String batchNo) {
        return Result.success(importService.batchStart(batchNo));
    }

    @PutMapping("/batch-pause/{batchNo}")
    @Operation(summary = "批暂停")
    @RequirePermission("batch:task:import")
    public Result<Integer> batchPause(
            @Parameter(description = "批次号", required = true) @PathVariable String batchNo) {
        return Result.success(importService.batchPause(batchNo));
    }

    @GetMapping("/batches")
    @Operation(summary = "查询历史批次列表")
    @RequirePermission("batch:task:import")
    public Result<List<TaskImportBatchVO>> listBatches(
            @Parameter(description = "搜索关键词(文件名/批次号)") @RequestParam(required = false) String keyword) {
        return Result.success(importService.listBatches(keyword));
    }

    @DeleteMapping("/batch/{batchNo}")
    @Operation(summary = "删除批次")
    @RequirePermission("batch:task:import")
    public Result<Void> deleteBatch(
            @Parameter(description = "批次号", required = true) @PathVariable String batchNo) {
        importService.deleteBatch(batchNo);
        return Result.success();
    }

    private List<BatchTaskImportDTO> buildTemplateExamples() {
        List<BatchTaskImportDTO> examples = new ArrayList<>();

        BatchTaskImportDTO example1 = new BatchTaskImportDTO();
        example1.setTransferMode("ONE_TO_ONE");
        example1.setTaskName("log-transfer-one-to-one");
        example1.setTaskDescription("One-to-one log transfer from node1 to node2");
        example1.setSourceAgentName("root@10.0.0.1:7777");
        example1.setSourceDir("/data/logs");
        example1.setTargetAgentNames("root@10.0.0.2:7777");
        example1.setTargetDirs("/backup/logs");
        example1.setIncludePatterns("*.log;*.txt");
        example1.setExcludePatterns("*.tmp");
        example1.setScanCronExpression("0 0/5 * * * ?");
        example1.setMaxScanFiles(5000);
        example1.setRetryEnabled(1);
        example1.setRetryMaxDays(7);
        example1.setRetryIntervalMin(30);
        example1.setMaxRetryCount(10);
        example1.setRetryBackoffType("EXPONENTIAL");
        example1.setPostTransferAction("NONE");
        example1.setBackupDir("");
        example1.setBackupMode("COPY");
        example1.setPreserveDirStructure(1);
        example1.setRoutingStrategy("BROADCAST");
        example1.setRoutingConfig("");
        example1.setScheduledEnabled(0);
        example1.setScheduledStartTime("");
        example1.setScheduledEndTime("");
        example1.setTaskPriority(5);
        example1.setRemark("Basic one-to-one example");
        examples.add(example1);

        BatchTaskImportDTO example2 = new BatchTaskImportDTO();
        example2.setTransferMode("ONE_TO_MANY");
        example2.setTaskName("broadcast-sync");
        example2.setTaskDescription("One-to-many broadcast with backup and scheduled transfer");
        example2.setSourceAgentName("root@10.0.0.1:7777");
        example2.setSourceDir("/data/src");
        example2.setTargetAgentNames("root@10.0.0.2:7777;root@10.0.0.3:7777;root@10.0.0.4:7777");
        example2.setTargetDirs("/data/dst2;/data/dst3;/data/dst4");
        example2.setIncludePatterns("*.*");
        example2.setExcludePatterns("*.bak;*.swp");
        example2.setScanCronExpression("0 0 2 * * ?");
        example2.setMaxScanFiles(10000);
        example2.setRetryEnabled(1);
        example2.setRetryMaxDays(14);
        example2.setRetryIntervalMin(60);
        example2.setMaxRetryCount(20);
        example2.setRetryBackoffType("LINEAR");
        example2.setPostTransferAction("BACKUP");
        example2.setBackupDir("/data/backup");
        example2.setBackupMode("MOVE");
        example2.setPreserveDirStructure(1);
        example2.setRoutingStrategy("BROADCAST");
        example2.setRoutingConfig("");
        example2.setScheduledEnabled(1);
        example2.setScheduledStartTime("08:00:00");
        example2.setScheduledEndTime("20:00:00");
        example2.setTaskPriority(3);
        example2.setRemark("Broadcast with backup and time window");
        examples.add(example2);

        BatchTaskImportDTO example3 = new BatchTaskImportDTO();
        example3.setTransferMode("ONE_TO_MANY");
        example3.setTaskName("round-robin-distribute");
        example3.setTaskDescription("Round-robin distribution with delete-after-transfer");
        example3.setSourceAgentName("root@10.0.0.5:7777");
        example3.setSourceDir("/tmp/upload");
        example3.setTargetAgentNames("root@10.0.0.6:7777;root@10.0.0.7:7777");
        example3.setTargetDirs("/data/inbox6;/data/inbox7");
        example3.setIncludePatterns("*.csv;*.json");
        example3.setExcludePatterns("*.bak");
        example3.setScanCronExpression("0 0/10 * * * ?");
        example3.setMaxScanFiles(2000);
        example3.setRetryEnabled(0);
        example3.setRetryMaxDays(3);
        example3.setRetryIntervalMin(10);
        example3.setMaxRetryCount(5);
        example3.setRetryBackoffType("LINEAR");
        example3.setPostTransferAction("DELETE");
        example3.setBackupDir("");
        example3.setBackupMode("COPY");
        example3.setPreserveDirStructure(0);
        example3.setRoutingStrategy("ROUND_ROBIN");
        example3.setRoutingConfig("");
        example3.setScheduledEnabled(0);
        example3.setScheduledStartTime("");
        example3.setScheduledEndTime("");
        example3.setTaskPriority(8);
        example3.setRemark("Round-robin with delete source");
        examples.add(example3);

        BatchTaskImportDTO example4 = new BatchTaskImportDTO();
        example4.setTransferMode("ONE_TO_ONE");
        example4.setTaskName("minimal-config");
        example4.setTaskDescription("Minimal required fields only, defaults will be applied");
        example4.setSourceAgentName("root@10.0.0.1:7777");
        example4.setSourceDir("/home/user/data");
        example4.setTargetAgentNames("root@10.0.0.2:7777");
        example4.setTargetDirs("/home/user/backup");
        example4.setIncludePatterns("*");
        example4.setExcludePatterns("");
        example4.setScanCronExpression("0 0/30 * * * ?");
        example4.setMaxScanFiles(10000);
        example4.setRetryEnabled(1);
        example4.setRetryMaxDays(7);
        example4.setRetryIntervalMin(30);
        example4.setMaxRetryCount(10);
        example4.setRetryBackoffType("EXPONENTIAL");
        example4.setPostTransferAction("NONE");
        example4.setBackupDir("");
        example4.setBackupMode("COPY");
        example4.setPreserveDirStructure(1);
        example4.setRoutingStrategy("BROADCAST");
        example4.setRoutingConfig("");
        example4.setScheduledEnabled(0);
        example4.setScheduledStartTime("");
        example4.setScheduledEndTime("");
        example4.setTaskPriority(5);
        example4.setRemark("Only required fields, all optional fields use defaults");
        examples.add(example4);

        BatchTaskImportDTO example5 = new BatchTaskImportDTO();
        example5.setTransferMode("ONE_TO_ONE");
        example5.setTaskName("test");
        example5.setTaskDescription("One-to-one log transfer from node1 to node2");
        example5.setSourceAgentName("dell@192.168.1.8:7777");
        example5.setSourceDir("E:/tmp");
        example5.setTargetAgentNames("cq@172.19.200.130:7777");
        example5.setTargetDirs("/tmp/r1");
        example5.setIncludePatterns("a*.txt");
        example5.setExcludePatterns("*.tmp");
        example5.setScanCronExpression("0 0/1 * * * ?");
        example5.setMaxScanFiles(5000);
        example5.setRetryEnabled(1);
        example5.setRetryMaxDays(7);
        example5.setRetryIntervalMin(1);
        example5.setMaxRetryCount(10);
        example5.setRetryBackoffType("EXPONENTIAL");
        example5.setPostTransferAction("NONE");
        example5.setBackupDir("");
        example5.setBackupMode("COPY");
        example5.setPreserveDirStructure(1);
        example5.setRoutingStrategy("BROADCAST");
        example5.setRoutingConfig("");
        example5.setScheduledEnabled(0);
        example5.setScheduledStartTime("");
        example5.setScheduledEndTime("");
        example5.setTaskPriority(5);
        example5.setRemark("Basic one-to-one example");
        examples.add(example5);

        return examples;
    }
}
