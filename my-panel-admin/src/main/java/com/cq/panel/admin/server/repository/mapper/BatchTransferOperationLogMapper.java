package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferOperationLog;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface BatchTransferOperationLogMapper
{
    int insertBatchTransferOperationLog(BatchTransferOperationLog entity);

    List<BatchTransferOperationLog> selectByTaskId(Long taskId);

    List<BatchTransferOperationLog> selectList(BatchTransferOperationLog query);
}
