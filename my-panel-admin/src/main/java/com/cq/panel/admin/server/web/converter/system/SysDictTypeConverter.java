package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysDictType;
import com.cq.panel.admin.server.web.domain.dto.system.SysDictTypeDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysDictTypeQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysDictTypeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Mapper(componentModel = "spring")
public interface SysDictTypeConverter {
    SysDictTypeConverter INSTANCE = Mappers.getMapper(SysDictTypeConverter.class);

    SysDictType toEntity(SysDictTypeDTO dto);

    SysDictTypeVO toVO(SysDictType entity);
    
    List<SysDictTypeVO> toVOList(List<SysDictType> list);

    @Mapping(target = "params", expression = "java(mapParams(query))")
    SysDictType toEntity(SysDictTypeQueryDTO query);

    default Map<String, Object> mapParams(SysDictTypeQueryDTO query) {
        if (query.getBeginTime() == null && query.getEndTime() == null) {
            return new HashMap<>();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("beginTime", query.getBeginTime());
        params.put("endTime", query.getEndTime());
        return params;
    }
}




