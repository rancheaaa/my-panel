package com.cq.panel.admin.server.web.domain.dto.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Agent配置校验中的任务项
 * 对应Agent端 GET /api/config/verify 返回的tasks数组中的元素
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentConfigTaskItem {

    /** 任务ID */
    private Long taskId;

    /** 任务配置内容JSON字符串 */
    private String content;
}
