package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface IBatchTransferSubtaskService
{
    BatchTransferSubtask selectById(Long id);

    List<BatchTransferSubtask> selectByTaskId(Long taskId);

    List<BatchTransferSubtask> selectByTaskIdAndStatus(Long taskId, String status);

    List<BatchTransferSubtask> selectList(BatchTransferSubtask query);

    int insert(BatchTransferSubtask entity);

    int update(BatchTransferSubtask entity);

    int updateStatus(Long id, String status);

    int batchInsert(List<BatchTransferSubtask> list);

    List<BatchTransferSubtask> selectFailedForRetry(Date beforeTime);

    Map<String, Object> selectSubtaskSummaryByTaskId(Long taskId);
}
