package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.repository.domain.SysLoginInfo;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysLogininforQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysLogininforVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface SysLogininforConverter {

    SysLogininforVO toVO(SysLoginInfo entity);

    List<SysLogininforVO> toVOList(List<SysLoginInfo> list);

    @Mapping(target = "params", source = "dto", qualifiedByName = "buildParams")
    SysLoginInfo toEntity(SysLogininforQueryDTO dto);

    @Named("buildParams")
    static Map<String, Object> buildParams(SysLogininforQueryDTO dto) {
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