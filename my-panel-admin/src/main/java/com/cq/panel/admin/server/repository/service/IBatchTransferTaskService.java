package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import java.util.List;

public interface IBatchTransferTaskService
{
    BatchTransferTask selectById(Long id);

    List<BatchTransferTask> selectList(BatchTransferTask query);

    int insert(BatchTransferTask entity);

    int update(BatchTransferTask entity);

    int deleteByIds(Long[] ids);

    int updateStatus(Long id, String status);
}
