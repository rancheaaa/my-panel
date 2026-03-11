package com.cq.panel.admin.server.web.converter.rc;

import com.cq.panel.admin.server.repository.domain.RcEnv;
import com.cq.panel.admin.server.web.domain.dto.rc.RcEnvDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcEnvQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.rc.RcEnvVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

/**
 * 环境管理 转换器
 * 
 * @author cq
 */
@Mapper(componentModel = "spring")
public interface RcEnvConverter {
    RcEnvConverter INSTANCE = Mappers.getMapper(RcEnvConverter.class);

    RcEnv toEntity(RcEnvDTO dto);

    RcEnvVO toVO(RcEnv entity);
    
    List<RcEnvVO> toVOList(List<RcEnv> list);

    RcEnv toEntity(RcEnvQueryDTO query);
}
