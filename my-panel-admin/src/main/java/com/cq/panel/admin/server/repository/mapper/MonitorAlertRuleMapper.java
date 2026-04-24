package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertRule;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MonitorAlertRuleMapper {
    List<MonitorAlertRule> selectAll();

    List<MonitorAlertRule> selectEnabledRules();

    int insert(MonitorAlertRule rule);

    int update(MonitorAlertRule rule);

    int deleteById(@Param("id") Long id);
}
