package com.cq.panel.admin.server.web.converter.agent;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.web.domain.dto.agent.AgentRegistryDTO;
import com.cq.panel.admin.server.web.domain.dto.agent.AgentRegistryQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.agent.AgentRegistryVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

/**
 * Agent注册信息 转换器
 * 
 * @author cq
 */
@Mapper(componentModel = "spring")
public interface AgentRegistryConverter {
    AgentRegistryConverter INSTANCE = Mappers.getMapper(AgentRegistryConverter.class);

    AgentRegistry toEntity(AgentRegistryDTO dto);

    AgentRegistryVO toVO(AgentRegistry entity);
    
    List<AgentRegistryVO> toVOList(List<AgentRegistry> list);

    AgentRegistry toEntity(AgentRegistryQueryDTO query);
}