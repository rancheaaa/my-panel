package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysNotice;
import com.cq.panel.admin.server.web.domain.dto.system.SysNoticeDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysNoticeQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysNoticeVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysNoticeConverter {
    SysNoticeConverter INSTANCE = Mappers.getMapper(SysNoticeConverter.class);

    SysNotice toEntity(SysNoticeDTO dto);

    SysNoticeVO toVO(SysNotice entity);
    
    List<SysNoticeVO> toVOList(List<SysNotice> list);

    SysNotice toEntity(SysNoticeQueryDTO query);
}




