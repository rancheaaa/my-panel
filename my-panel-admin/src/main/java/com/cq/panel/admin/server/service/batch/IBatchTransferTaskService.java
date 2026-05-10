package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.service.batch.dto.BatchTransferTaskDTO;

import java.util.List;
import java.util.Map;

/**
 * 批量传输任务服务接口
 */
public interface IBatchTransferTaskService {

    Long createTask(BatchTransferTaskDTO dto, String userId);

    void updateTask(Long taskId, BatchTransferTaskDTO dto);

    void startTask(Long taskId);

    void pauseTask(Long taskId);

    void resumeTask(Long taskId);

    void stopTask(Long taskId);

    void deleteTasks(List<Long> taskIds);

    BatchTransferTask getTaskById(Long taskId);

    List<BatchTransferTask> getTaskList(String status, String sourceAgentId, Integer pageNum, Integer pageSize);

    Map<String, Object> getStatistics();
}
