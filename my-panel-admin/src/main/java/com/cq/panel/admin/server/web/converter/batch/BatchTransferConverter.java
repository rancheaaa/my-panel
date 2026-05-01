package com.cq.panel.admin.server.web.converter.batch;

import com.cq.panel.admin.server.repository.domain.BatchAlertEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskCreateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchAlertEventVO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchSubtaskVO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface BatchTransferConverter
{
    BatchTransferConverter INSTANCE = Mappers.getMapper(BatchTransferConverter.class);

    @Mapping(target = "includePatterns", source = "includePatterns", qualifiedByName = "listToString")
    @Mapping(target = "excludePatterns", source = "excludePatterns", qualifiedByName = "listToString")
    @Mapping(target = "targetAgents", source = "targetAgents", qualifiedByName = "listToString")
    @Mapping(target = "retryEnabled", source = "retryEnabled", qualifiedByName = "booleanToInteger")
    @Mapping(target = "preserveDirStructure", source = "preserveDirStructure", qualifiedByName = "booleanToInteger")
    BatchTransferTask toEntity(BatchTaskCreateDTO dto);

    BatchTransferTask toEntity(BatchTaskQueryDTO query);

    BatchTaskVO toVO(BatchTransferTask entity);

    List<BatchTaskVO> toVOList(List<BatchTransferTask> list);

    BatchSubtaskVO toSubtaskVO(BatchTransferSubtask entity);

    List<BatchSubtaskVO> toSubtaskVOList(List<BatchTransferSubtask> list);

    BatchAlertEventVO toAlertVO(BatchAlertEvent entity);

    List<BatchAlertEventVO> toAlertVOList(List<BatchAlertEvent> list);

    @Named("listToString")
    default String listToString(List<String> list)
    {
        if (list == null || list.isEmpty()) return null;
        return new com.google.gson.Gson().toJson(list);
    }

    @Named("booleanToInteger")
    default Integer booleanToInteger(Boolean value)
    {
        if (value == null) return 0;
        return value ? 1 : 0;
    }
}
