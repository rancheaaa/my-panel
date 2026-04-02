package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.repository.domain.SysLoginInfo;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysLogininforQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysLogininforVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SysLogininforConverter {

    SysLogininforVO toVO(SysLoginInfo entity);

    List<SysLogininforVO> toVOList(List<SysLoginInfo> list);

    SysLoginInfo toEntity(SysLogininforQueryDTO dto);
}



