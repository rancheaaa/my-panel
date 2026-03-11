package com.cq.panel.admin.server.web.converter.rc;

import com.cq.panel.admin.server.repository.domain.RcProject;
import com.cq.panel.admin.server.web.domain.dto.rc.RcProjectDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcProjectQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.rc.RcProjectVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

/**
 * 应用管理 转换器
 * 
 * @author cq
 */
@Mapper(componentModel = "spring")
public interface RcProjectConverter {
    RcProjectConverter INSTANCE = Mappers.getMapper(RcProjectConverter.class);

    RcProject toEntity(RcProjectDTO dto);

    RcProjectVO toVO(RcProject entity);
    
    List<RcProjectVO> toVOList(List<RcProject> list);

    RcProject toEntity(RcProjectQueryDTO query);
}
