package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserResetPwdDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserStatusDTO;
import com.cq.panel.admin.server.web.domain.dto.system.UserProfileUpdateDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysUserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring", uses = {SysRoleConverter.class})
public interface SysUserConverter {
    SysUserConverter INSTANCE = Mappers.getMapper(SysUserConverter.class);

    SysUser toEntity(SysUserQueryDTO query);
    SysUser toEntity(SysUserDTO dto);
    SysUser toEntity(SysUserResetPwdDTO dto);
    SysUser toEntity(SysUserStatusDTO dto);
    
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "deptId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "loginIp", ignore = true)
    @Mapping(target = "loginDate", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "remark", ignore = true)
    void updateEntity(@MappingTarget SysUser user, UserProfileUpdateDTO dto);

    SysUser toEntity(UserProfileUpdateDTO dto);

    SysUserVO toVO(SysUser entity);
    
    List<SysUserVO> toVOList(List<SysUser> list);
}







