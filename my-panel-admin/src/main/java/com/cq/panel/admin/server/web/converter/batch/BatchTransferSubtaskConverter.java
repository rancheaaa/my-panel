package com.cq.panel.admin.server.web.converter.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTransferSubtaskVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 批量传输子任务 转换器
 *
 * @author cq
 */
@Mapper(componentModel = "spring")
public interface BatchTransferSubtaskConverter {

    BatchTransferSubtaskConverter INSTANCE = Mappers.getMapper(BatchTransferSubtaskConverter.class);

    BatchTransferSubtaskVO toVO(BatchTransferSubtask entity);

    List<BatchTransferSubtaskVO> toVOList(List<BatchTransferSubtask> list);
}
