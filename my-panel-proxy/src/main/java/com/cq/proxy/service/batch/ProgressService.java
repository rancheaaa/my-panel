package com.cq.proxy.service.batch;

import java.util.List;

/**
 * 进度更新服务接口
 * 处理Agent上报的子任务进度信息
 */
public interface ProgressService {

    /**
     * 更新单个子任务进度
     * @param subtaskId 子任务ID
     * @param transferredBytes 已传输字节数
     * @param totalBytes 总字节数
     */
    void updateProgress(Long subtaskId, int transferredBytes, int totalBytes);

    /**
     * 检查数据是否过期（基于时间戳）
     * @param subtaskId 子任务ID
     * @param timestamp 时间戳
     * @return true表示数据已过期
     */
    boolean isStaleData(Long subtaskId, Long timestamp);

    /**
     * 批量更新进度
     * @param progressList 进度列表
     */
    void batchUpdateProgress(List<?> progressList);

    /**
     * 标记子任务完成
     * @param subtaskId 子任务ID
     * @param targetPath 目标路径
     */
    void markCompleted(Long subtaskId, String targetPath);

    /**
     * 标记子任务失败
     * @param subtaskId 子任务ID
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     */
    void markFailed(Long subtaskId, String errorCode, String errorMessage);

    /**
     * 调度下次重试
     * @param subtaskId 子任务ID
     * @return 下次重试时间戳
     */
    long scheduleNextRetry(Long subtaskId);
}
