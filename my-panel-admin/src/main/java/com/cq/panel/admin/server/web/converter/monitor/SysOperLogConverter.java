package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.repository.domain.SysOperLog;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysOperLogQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysOperLogVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SysOperLogConverter {

    SysOperLogVO toVO(SysOperLog entity);

    List<SysOperLogVO> toVOList(List<SysOperLog> list);

    SysOperLog toEntity(SysOperLogQueryDTO dto);
}



