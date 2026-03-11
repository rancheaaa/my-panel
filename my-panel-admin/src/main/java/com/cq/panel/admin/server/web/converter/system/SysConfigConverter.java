package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysConfig;
import com.cq.panel.admin.server.web.domain.dto.system.SysConfigDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import java.util.List;

import com.cq.panel.admin.server.web.domain.dto.system.SysConfigQueryDTO;
import java.util.Map;
import java.util.HashMap;

@Mapper(componentModel = "spring")
public interface SysConfigConverter {
    SysConfigConverter INSTANCE = Mappers.getMapper(SysConfigConverter.class);

    SysConfig toEntity(SysConfigDTO dto);

    SysConfigVO toVO(SysConfig entity);
    
    List<SysConfigVO> toVOList(List<SysConfig> list);

    @Mapping(target = "params", expression = "java(mapParams(query))")
    SysConfig toEntity(SysConfigQueryDTO query);

    default Map<String, Object> mapParams(SysConfigQueryDTO query) {
        if (query.beginTime() == null && query.endTime() == null) {
            return new HashMap<>();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("beginTime", query.beginTime());
        params.put("endTime", query.endTime());
        return params;
    }
}




