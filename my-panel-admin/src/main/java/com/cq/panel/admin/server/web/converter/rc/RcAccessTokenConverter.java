package com.cq.panel.admin.server.web.converter.rc;

import com.cq.panel.admin.server.repository.domain.RcAccessToken;
import com.cq.panel.admin.server.web.domain.dto.rc.RcAccessTokenDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcAccessTokenQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.rc.RcAccessTokenVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

/**
 * AccessToken管理 转换器
 * 
 * @author cq
 */
@Mapper(componentModel = "spring")
public interface RcAccessTokenConverter {
    RcAccessTokenConverter INSTANCE = Mappers.getMapper(RcAccessTokenConverter.class);

    RcAccessToken toEntity(RcAccessTokenDTO dto);

    RcAccessTokenVO toVO(RcAccessToken entity);
    
    List<RcAccessTokenVO> toVOList(List<RcAccessToken> list);

    RcAccessToken toEntity(RcAccessTokenQueryDTO query);
}
