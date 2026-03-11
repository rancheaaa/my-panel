package com.cq.panel.admin.server.web.converter.rc;

import com.cq.panel.admin.server.repository.domain.RcNode;
import com.cq.panel.admin.server.web.domain.dto.rc.RcNodeDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcNodeQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.rc.RcNodeVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

/**
 * 注册中心节点 转换器
 * 
 * @author cq
 */
@Mapper(componentModel = "spring")
public interface RcNodeConverter {
    RcNodeConverter INSTANCE = Mappers.getMapper(RcNodeConverter.class);

    RcNode toEntity(RcNodeDTO dto);

    RcNodeVO toVO(RcNode entity);
    
    List<RcNodeVO> toVOList(List<RcNode> list);

    RcNode toEntity(RcNodeQueryDTO query);
}
