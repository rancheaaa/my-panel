package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.repository.domain.SysJobLog;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobLogQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysJobLogVO;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysJobLogConverter {

    SysJobLogVO toVO(SysJobLog entity);

    List<SysJobLogVO> toVOList(List<SysJobLog> list);

    SysJobLog toEntity(SysJobLogQueryDTO dto);
}



