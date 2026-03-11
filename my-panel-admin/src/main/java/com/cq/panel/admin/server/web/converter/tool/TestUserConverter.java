package com.cq.panel.admin.server.web.converter.tool;

import com.cq.panel.admin.server.web.domain.dto.tool.TestUserDTO;
import com.cq.panel.admin.server.web.domain.vo.tool.TestUserVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TestUserConverter {
    TestUserVO toVO(TestUserDTO dto);
}



