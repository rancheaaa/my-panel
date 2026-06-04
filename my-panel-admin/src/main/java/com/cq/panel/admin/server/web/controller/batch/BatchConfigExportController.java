package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.service.batch.BatchConfigSerializer;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchConfigExportDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Tag(name = "批量配置导出", description = "Agent配置文件导出相关接口")
@RestController
@RequestMapping("/batch/config-export")
@Validated
public class BatchConfigExportController extends BaseController {

    private final BatchTransferTaskMapper batchTransferTaskMapper;
    private final BatchConfigSerializer batchConfigSerializer;

    public BatchConfigExportController(BatchTransferTaskMapper batchTransferTaskMapper,
                                       BatchConfigSerializer batchConfigSerializer) {
        this.batchTransferTaskMapper = batchTransferTaskMapper;
        this.batchConfigSerializer = batchConfigSerializer;
    }

    @Operation(summary = "导出Agent配置文件", description = "根据源节点ID导出该节点的所有传输任务配置，生成zip包下载")
    @Log(title = "配置导出", businessType = BusinessType.EXPORT)
    @RequirePermission("batch:config:export")
    @PostMapping("/download")
    public void downloadConfig(@Valid @RequestBody BatchConfigExportDTO dto,
                               HttpServletResponse response) throws IOException {
        // 1. 查询该源节点的所有未删除任务
        BatchTransferTask query = new BatchTransferTask();
        query.setSourceAgentId(dto.getSourceAgentId());
        query.setDeleted(0);
        List<BatchTransferTask> tasks = batchTransferTaskMapper.selectList(query);

        if (tasks == null || tasks.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"msg\":\"该节点没有传输任务\"}");
            return;
        }

        // 2. 生成 task_*.json 文件内容
        Map<String, String> taskJsonMap = new LinkedHashMap<>();
        List<Map<String, Object>> metaTasks = new ArrayList<>();
        long now = System.currentTimeMillis();
        String isoTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date(now));

        for (BatchTransferTask task : tasks) {
            try {
                String json = batchConfigSerializer.serializeForAgent(task);
                String fileName = "task_" + task.getId() + ".json";
                taskJsonMap.put(fileName, json);

                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("taskId", task.getId());
                meta.put("taskName", task.getTaskName());
                meta.put("status", task.getStatus());
                meta.put("version", task.getUpdateTime() != null
                        ? Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(task.getUpdateTime()))
                        : 0L);
                meta.put("receivedAt", isoTime);
                meta.put("persistedAt", isoTime);
                meta.put("lastModified", now);
                metaTasks.add(meta);
            } catch (Exception e) {
                // 跳过序列化失败的任务
            }
        }

        if (taskJsonMap.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"msg\":\"没有可导出的任务配置\"}");
            return;
        }

        // 3. 生成 tasks.meta.json（Gson已配置PrettyPrinting）
        Map<String, Object> metaData = new LinkedHashMap<>();
        metaData.put("tasks", metaTasks);
        String metaJson = batchConfigSerializer.serialize(metaData);

        // 4. 写入 zip 响应
        String zipFileName = "batch-config-" + dto.getSourceAgentId() + "-" +
                new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(zipFileName, StandardCharsets.UTF_8));

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            // 写入 tasks.meta.json
            ZipEntry metaEntry = new ZipEntry("tasks.meta.json");
            zos.putNextEntry(metaEntry);
            zos.write(metaJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 写入 task_*.json
            for (Map.Entry<String, String> entry : taskJsonMap.entrySet()) {
                ZipEntry taskEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(taskEntry);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.flush();
        }
    }
}
