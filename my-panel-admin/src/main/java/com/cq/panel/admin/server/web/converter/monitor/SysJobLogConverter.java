package com.cq.panel.admin.server.web.converter.monitor;

import com.cq.panel.admin.server.common.utils.MyDateUtils;
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
    @Mapping(target = "triggerType", source = "triggerType")
    @Mapping(target = "startTimeStart", source = "startTimeStart", qualifiedByName = "parseDateTime")
    @Mapping(target = "startTimeEnd", source = "startTimeEnd", qualifiedByName = "parseDateTime")
    @Mapping(target = "endTimeStart", source = "endTimeStart", qualifiedByName = "parseDateTime")
    @Mapping(target = "endTimeEnd", source = "endTimeEnd", qualifiedByName = "parseDateTime")
    SysJobLog toEntity(SysJobLogQueryDTO dto);

    List<SysJobLogVO> toVOList(List<SysJobLog> list);

    @Named("formatDateTime")
    static String formatDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return MyDateUtils.parseDateToStr(MyDateUtils.YYYY_MM_DD_HH_MM_SS, date);
    }

    @Named("parseDateTime")
    static Date parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            Date date = MyDateUtils.parseDate(dateStr);
            if (date == null) {
                throw new RuntimeException("日期解析失败: " + dateStr);
            }
            return date;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(SysJobLogConverter.class)
                .error("日期解析失败 - 值: {}, 支持的格式: yyyy-MM-dd, yyyy-MM-dd HH:mm:ss 等", dateStr, e);
            return null;
        }
    }
}