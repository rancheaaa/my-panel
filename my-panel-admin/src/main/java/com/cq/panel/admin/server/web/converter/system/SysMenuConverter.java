package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysMenu;
import com.cq.panel.admin.server.web.domain.dto.system.SysMenuDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysMenuQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysMenuVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysMenuConverter {
    SysMenuConverter INSTANCE = Mappers.getMapper(SysMenuConverter.class);

    SysMenu toEntity(SysMenuDTO dto);

    SysMenuVO toVO(SysMenu entity);
    
    List<SysMenuVO> toVOList(List<SysMenu> list);

    SysMenu toEntity(SysMenuQueryDTO query);
}




