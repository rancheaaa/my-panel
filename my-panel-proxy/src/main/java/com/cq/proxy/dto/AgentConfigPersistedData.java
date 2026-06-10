package com.cq.proxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Agent配置持久化确认响应数据
 * 对应 {"success":true,"data":{"configPersisted":true}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentConfigPersistedData {

    /** 配置是否已持久化 */
    private Boolean configPersisted;
}
