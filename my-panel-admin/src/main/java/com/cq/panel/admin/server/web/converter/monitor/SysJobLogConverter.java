package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.common.utils.DateUtils;
import com.cq.panel.admin.server.repository.domain.SysJobLog;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobLogQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysJobLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import java.util.Date;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SysJobLogConverter {

    @Mapping(target = "startTime", source = "startTime", qualifiedByName = "formatDateTime")
    @Mapping(target = "endTime", source = "endTime", qualifiedByName = "formatDateTime")
    @Mapping(target = "triggerType", source = "triggerType")
    SysJobLogVO toVO(SysJobLog entity);

    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    SysJobLog toEntity(SysJobLogQueryDTO dto);

    List<SysJobLogVO> toVOList(List<SysJobLog> list);

    @Named("formatDateTime")
    static String formatDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, date);
    }
}