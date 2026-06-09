package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.BatchTransferSubtask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 批量传输子任务Mapper (Proxy端更新进度用)
 */
@Mapper
public interface BatchTransferSubtaskMapper {

    /**
     * 插入子任务
     */
    int insert(BatchTransferSubtask subtask);

    /**
     * 根据实体更新子任务（只更新非空字段）
     */
    int updateByEntity(BatchTransferSubtask subtask);

    /**
     * 更新进度字段
     */
    int updateProgress(
            @Param("id") Long id,
            @Param("transferredChunks") Integer transferredChunks,
            @Param("totalChunks") Integer totalChunks,
            @Param("transferredBytes") Long transferredBytes,
            @Param("speedBytesPerSec") Long speedBytesPerSec);

    /**
     * 更新状态为COMPLETED
     */
    int updateStatusCompleted(
            @Param("id") Long id,
            @Param("durationMs") Long durationMs);

    /**
     * 更新状态为FAILED
     */
    int updateStatusFailed(
            @Param("id") Long id,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    /**
     * 更新状态为RETRYING
     * Agent是重试计数的唯一权威来源，直接使用Agent上报的retryCount，不自增
     */
    int updateStatusRetrying(
            @Param("id") Long id,
            @Param("nextRetryAfter") Date nextRetryAfter,
            @Param("retryCount") Integer retryCount);
}
