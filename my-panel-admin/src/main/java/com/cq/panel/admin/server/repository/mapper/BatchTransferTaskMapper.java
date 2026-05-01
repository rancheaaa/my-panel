package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BatchTransferTaskMapper
{
    BatchTransferTask selectBatchTransferTaskById(Long id);

    List<BatchTransferTask> selectBatchTransferTaskList(BatchTransferTask query);

    int insertBatchTransferTask(BatchTransferTask entity);

    int updateBatchTransferTask(BatchTransferTask entity);

    int deleteBatchTransferTaskByIds(Long[] ids);

    int updateTaskStatus(@Param("id") Long id, @Param("status") String status);
}
