package com.cq.panel.admin.server.web.converter.rc;

import com.cq.panel.admin.server.repository.domain.RcConfig;
import com.cq.panel.admin.server.web.domain.dto.rc.RcConfigDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcConfigQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.rc.RcConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

/**
 * 配置中心 转换器
 * 
 * @author cq
 */
@Mapper(componentModel = "spring")
public interface RcConfigConverter {
    RcConfigConverter INSTANCE = Mappers.getMapper(RcConfigConverter.class);

    RcConfig toEntity(RcConfigDTO dto);

    RcConfigVO toVO(RcConfig entity);
    
    List<RcConfigVO> toVOList(List<RcConfig> list);

    RcConfig toEntity(RcConfigQueryDTO query);
}
