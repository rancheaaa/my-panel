package com.cq.proxy.service.batch;

import com.cq.proxy.dto.SubTaskDTO;
import com.cq.proxy.repository.entity.BatchTransferSubtask;

import java.util.Date;

/**
 * 进度更新服务接口
 * 处理Agent上报的子任务进度信息
 */
public interface ProgressService {

    /**
     * 创建子任务（INSERT）
     */
    Long createSubTask(BatchTransferSubtask subtask);

    /**
     * 更新单个子任务进度
     */
    void updateProgress(Long subtaskId, int transferredBytes, int totalBytes);

    /**
     * 扩展版进度更新（包含chunks和speed信息）
     */
    void updateProgressExt(Long subtaskId, Integer transferredChunks, Integer totalChunks,
            Long transferredBytes, Long speedBytesPerSec);

    /**
     * 检查数据是否过期（基于时间戳）
     */
    boolean isStaleData(Long subtaskId, Long timestamp);

    /**
     * 批量更新进度（使用DTO数组）
     */
    void batchUpdateProgress(SubTaskDTO[] dtoArray);

    /**
     * 更新完整子任务状态
     */
    void updateSubTaskStatus(BatchTransferSubtask subtask);

    /**
     * 标记子任务完成
     */
    void markCompleted(Long subtaskId, String targetPath);

    /**
     * 标记子任务失败
     */
    void markFailed(Long subtaskId, String errorCode, String errorMessage, String errorStackTrace);

    /**
     * 调度下次重试
     * @param subtaskId 子任务ID
     * @param retryCount Agent端上报的重试次数（Agent是重试计数的唯一权威来源）
     * @param nextRetryAfter 下一次重试时间
     */
    void updateNextRetryTime(Long subtaskId, Integer retryCount, Date nextRetryAfter);
}
