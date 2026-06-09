package com.cq.panel.admin.server.web.domain.dto.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Agent配置列表响应数据
 * 对应Agent端 GET /api/config/verify 返回的data字段：{"tasks":[...]}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentConfigListData {

    /** 任务配置列表 */
    private List<AgentConfigTaskItem> tasks;
}
