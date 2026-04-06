package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.repository.domain.SysOperLog;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysOperLogQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysOperLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface SysOperLogConverter {

    SysOperLogVO toVO(SysOperLog entity);

    List<SysOperLogVO> toVOList(List<SysOperLog> list);

    @Mapping(target = "params", source = "dto", qualifiedByName = "buildParams")
    SysOperLog toEntity(SysOperLogQueryDTO dto);

    @Named("buildParams")
    static Map<String, Object> buildParams(SysOperLogQueryDTO dto) {
        Map<String, Object> params = new HashMap<>();
        if (dto.getBeginTime() != null && !dto.getBeginTime().isEmpty()) {
            params.put("beginTime", dto.getBeginTime());
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            params.put("endTime", dto.getEndTime());
        }
        if (dto.getParams() != null) {
            params.putAll(dto.getParams());
        }
        return params.isEmpty() ? null : params;
    }
}