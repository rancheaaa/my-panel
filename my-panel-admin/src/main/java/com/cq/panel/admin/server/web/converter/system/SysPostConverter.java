package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysPost;
import com.cq.panel.admin.server.web.domain.dto.system.SysPostDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysPostQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysPostVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysPostConverter {
    SysPostConverter INSTANCE = Mappers.getMapper(SysPostConverter.class);

    SysPost toEntity(SysPostDTO dto);

    SysPostVO toVO(SysPost entity);
    
    List<SysPostVO> toVOList(List<SysPost> list);

    SysPost toEntity(SysPostQueryDTO query);
}




