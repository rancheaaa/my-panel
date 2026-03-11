package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysDept;
import com.cq.panel.admin.server.web.domain.dto.system.SysDeptDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysDeptQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysDeptVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysDeptConverter {
    SysDeptConverter INSTANCE = Mappers.getMapper(SysDeptConverter.class);

    SysDept toEntity(SysDeptDTO dto);

    SysDeptVO toVO(SysDept entity);
    
    List<SysDeptVO> toVOList(List<SysDept> list);

    SysDept toEntity(SysDeptQueryDTO query);
}




