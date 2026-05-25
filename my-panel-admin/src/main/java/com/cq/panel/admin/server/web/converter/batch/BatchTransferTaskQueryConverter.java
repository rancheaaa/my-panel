package com.cq.panel.admin.server.web.converter.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.service.batch.dto.BatchTransferTaskQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface BatchTransferTaskQueryConverter {

    BatchTransferTaskQueryConverter INSTANCE = Mappers.getMapper(BatchTransferTaskQueryConverter.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "taskDescription", source = "taskDescription")
    @Mapping(target = "sourceAgentId", source = "sourceAgentId")
    @Mapping(target = "sourceAgentName", source = "sourceAgentName")
    @Mapping(target = "sourceDir", source = "sourceDir")
    @Mapping(target = "targetAgentIds", source = "targetAgentId")
    @Mapping(target = "targetAgentNames", source = "targetAgentName")
    @Mapping(target = "targetDirs", source = "targetDir")
    @Mapping(target = "includePatterns", ignore = true)
    @Mapping(target = "excludePatterns", ignore = true)
    @Mapping(target = "scanCronExpression", ignore = true)
    @Mapping(target = "maxScanFiles", ignore = true)
    @Mapping(target = "routingConfig", ignore = true)
    @Mapping(target = "backupDir", ignore = true)
    @Mapping(target = "backupMode", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "remark", ignore = true)
    BatchTransferTask toDomain(BatchTransferTaskQuery query);
}
