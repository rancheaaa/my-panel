package com.cq.proxy.web.controller.batch;

import com.cq.proxy.service.batch.ProgressAggregator;
import com.cq.proxy.service.batch.QueueMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "批量传输内部接口", description = "Agent上报接口(内部调用)")
@RestController
@RequestMapping("/api/internal/batch")
public class BatchInternalController
{
    private final ProgressAggregator progressAggregator;
    private final QueueMonitor queueMonitor;

    public BatchInternalController(ProgressAggregator progressAggregator, QueueMonitor queueMonitor)
    {
        this.progressAggregator = progressAggregator;
        this.queueMonitor = queueMonitor;
    }

    @Operation(summary = "接收进度上报")
    @PostMapping("/progress")
    public Map<String, Object> receiveProgress(@RequestBody Map<String, Object> report)
    {
        progressAggregator.receiveSubtaskProgress(report);
        return Map.of("success", true);
    }

    @Operation(summary = "批量进度上报")
    @PostMapping("/progress/batch")
    public Map<String, Object> receiveBatchProgress(@RequestBody java.util.List<Map<String, Object>> reports)
    {
        for (Map<String, Object> report : reports)
        {
            progressAggregator.receiveSubtaskProgress(report);
        }
        return Map.of("success", true, "count", reports.size());
    }

    @Operation(summary = "接收队列快照")
    @PostMapping("/queue/snapshot")
    public Map<String, Object> receiveQueueSnapshot(@RequestBody Map<String, Object> snapshot)
    {
        queueMonitor.receiveSnapshot(snapshot);
        return Map.of("success", true);
    }

    @Operation(summary = "接收后处理结果")
    @PostMapping("/post-process-result")
    public Map<String, Object> receivePostProcessResult(@RequestBody Map<String, Object> result)
    {
        progressAggregator.receivePostProcessResult(result);
        return Map.of("success", true);
    }
}
