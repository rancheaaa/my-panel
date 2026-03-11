package com.cq.panel.admin.server.web.converter.system;

import com.cq.panel.admin.server.repository.domain.SysDictData;
import com.cq.panel.admin.server.web.domain.dto.system.SysDictDataDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysDictDataQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysDictDataVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysDictDataConverter {
    SysDictDataConverter INSTANCE = Mappers.getMapper(SysDictDataConverter.class);

    SysDictData toEntity(SysDictDataDTO dto);

    SysDictDataVO toVO(SysDictData entity);
    
    List<SysDictDataVO> toVOList(List<SysDictData> list);

    SysDictData toEntity(SysDictDataQueryDTO query);
}




