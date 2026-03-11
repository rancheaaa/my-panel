package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysRole;
import com.cq.panel.admin.server.web.domain.dto.system.SysRoleDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysRoleQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysRoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysRoleConverter {
    SysRoleConverter INSTANCE = Mappers.getMapper(SysRoleConverter.class);

    SysRole toEntity(SysRoleDTO dto);

    SysRoleVO toVO(SysRole entity);
    
    List<SysRoleVO> toVOList(List<SysRole> list);

    SysRole toEntity(SysRoleQueryDTO query);
}




