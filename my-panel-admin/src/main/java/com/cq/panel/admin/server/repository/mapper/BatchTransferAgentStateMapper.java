package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferAgentState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BatchTransferAgentStateMapper
{
    List<BatchTransferAgentState> selectList(BatchTransferAgentState query);

    List<BatchTransferAgentState> selectByTaskId(@Param("taskId") Long taskId);

    BatchTransferAgentState selectByTransferId(@Param("transferId") String transferId);
}
