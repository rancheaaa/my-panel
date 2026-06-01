package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTransferTaskCreateDTO;

import java.util.List;
import java.util.Map;

/**
 * 批量传输任务服务接口
 */
public interface IBatchTransferTaskService {

    Long createTask(BatchTransferTaskCreateDTO dto, String userId);

    void updateTask(Long taskId, BatchTransferTaskCreateDTO dto, String userId);

    void startTask(Long taskId);

    void pauseTask(Long taskId);

    void resumeTask(Long taskId);

    void stopTask(Long taskId);

    void deleteTasks(List<Long> taskIds);

    BatchTransferTask getTaskById(Long taskId);

    List<BatchTransferTask> getTaskList(BatchTransferTask query, Integer pageNum, Integer pageSize);

    Map<String, Object> getStatistics();

    Map<String, Object> getTaskStatistics(Long taskId);

    long countByCondition(BatchTransferTask query);
}
