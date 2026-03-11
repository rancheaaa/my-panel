package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysJobVO;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysJobConverter {

    SysJobVO toVO(SysJob entity);

    List<SysJobVO> toVOList(List<SysJob> list);

    SysJob toEntity(SysJobDTO dto);
    
    SysJob toEntity(SysJobQueryDTO dto);
}




