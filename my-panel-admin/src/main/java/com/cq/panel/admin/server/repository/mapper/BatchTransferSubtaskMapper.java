package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface BatchTransferSubtaskMapper
{
    BatchTransferSubtask selectBatchTransferSubtaskById(Long id);

    List<BatchTransferSubtask> selectBatchTransferSubtaskList(BatchTransferSubtask query);

    List<BatchTransferSubtask> selectSubtasksByTaskId(Long taskId);

    List<BatchTransferSubtask> selectSubtasksByTaskIdAndStatus(@Param("taskId") Long taskId, @Param("status") String status);

    int insertBatchTransferSubtask(BatchTransferSubtask entity);

    int updateBatchTransferSubtask(BatchTransferSubtask entity);

    int updateSubtaskStatus(@Param("id") Long id, @Param("status") String status);

    int batchInsertSubtasks(@Param("list") List<BatchTransferSubtask> list);

    List<BatchTransferSubtask> selectFailedSubtasksForRetry(@Param("beforeTime") Date beforeTime);

    Map<String, Object> selectSubtaskSummaryByTaskId(Long taskId);
}
