package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.IBatchStatisticsService;
import com.cq.panel.admin.server.web.domain.vo.batch.SubtaskSummaryVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BatchStatisticsServiceImpl implements IBatchStatisticsService {

    private final BatchTransferSubtaskMapper subtaskMapper;
    private final BatchTransferTaskMapper taskMapper;

    public BatchStatisticsServiceImpl(BatchTransferSubtaskMapper subtaskMapper,
                                       BatchTransferTaskMapper taskMapper) {
        this.subtaskMapper = subtaskMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public SubtaskSummaryVO getSubtaskSummary() {
        SubtaskSummaryVO vo = new SubtaskSummaryVO();

        Map<String, Object> summary = subtaskMapper.countSummary();
        vo.setTotalCount(((Number) summary.getOrDefault("totalCount", 0L)).longValue());
        vo.setTotalSizeBytes(((Number) summary.getOrDefault("totalSizeBytes", 0L)).longValue());
        vo.setCompletedCount(((Number) summary.getOrDefault("completedCount", 0L)).longValue());
        vo.setCompletedSizeBytes(((Number) summary.getOrDefault("completedSizeBytes", 0L)).longValue());
        vo.setSendingCount(((Number) summary.getOrDefault("sendingCount", 0L)).longValue());
        vo.setFailedCount(((Number) summary.getOrDefault("failedCount", 0L)).longValue());
        vo.setQueuedCount(((Number) summary.getOrDefault("queuedCount", 0L)).longValue());
        vo.setRetryingCount(((Number) summary.getOrDefault("retryingCount", 0L)).longValue());

        Long totalTransferred = ((Number) summary.getOrDefault("totalTransferredBytes", 0L)).longValue();
        Double avgSpeed = (Double) summary.getOrDefault("avgSpeedBytesPerSec", 0D);

        if (vo.getTotalSizeBytes() != null && vo.getTotalSizeBytes() > 0) {
            BigDecimal progress = BigDecimal.valueOf(totalTransferred)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(vo.getTotalSizeBytes()), 2, RoundingMode.HALF_UP);
            vo.setProgressPercent(progress);
        } else {
            vo.setProgressPercent(BigDecimal.ZERO);
        }

        if (avgSpeed != null && avgSpeed > 0) {
            vo.setAvgSpeedBytesPerSec(BigDecimal.valueOf(avgSpeed).setScale(2, RoundingMode.HALF_UP));
        } else {
            vo.setAvgSpeedBytesPerSec(BigDecimal.ZERO);
        }

        List<Map<String, Object>> statusList = subtaskMapper.countGroupByStatus();
        Map<String, Long> statusDistribution = new HashMap<>();
        for (Map<String, Object> item : statusList) {
            String status = (String) item.get("status");
            Long count = ((Number) item.get("count")).longValue();
            statusDistribution.put(status, count);
        }
        vo.setStatusDistribution(statusDistribution);

        Long todayCount = subtaskMapper.countToday();
        vo.setTodayCount(todayCount != null ? todayCount : 0L);

        return vo;
    }

    @Override
    public TaskSummaryVO getTaskSummary() {
        TaskSummaryVO vo = new TaskSummaryVO();

        Map<String, Object> summary = taskMapper.countSummary();
        vo.setTotalCount(((Number) summary.getOrDefault("totalCount", 0L)).longValue());
        vo.setReadyCount(((Number) summary.getOrDefault("readyCount", 0L)).longValue());
        vo.setRunningCount(((Number) summary.getOrDefault("runningCount", 0L)).longValue());
        vo.setPausedCount(((Number) summary.getOrDefault("pausedCount", 0L)).longValue());
        vo.setActiveCount(vo.getRunningCount());

        List<Map<String, Object>> statusList = taskMapper.countGroupByStatus();
        Map<String, Long> statusDistribution = new HashMap<>();
        for (Map<String, Object> item : statusList) {
            String status = (String) item.get("status");
            Long count = ((Number) item.get("count")).longValue();
            statusDistribution.put(status, count);
        }
        vo.setStatusDistribution(statusDistribution);

        Long todayCount = taskMapper.countToday();
        vo.setTodayCount(todayCount != null ? todayCount : 0L);

        return vo;
    }
}
