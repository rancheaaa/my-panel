package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.repository.domain.SysUserOnline;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysUserOnlineVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SysUserOnlineConverter {

    SysUserOnlineVO toVO(SysUserOnline entity);

    List<SysUserOnlineVO> toVOList(List<SysUserOnline> list);
}


