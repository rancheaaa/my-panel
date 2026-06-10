package com.cq.proxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Agent删除指令确认响应数据
 * 对应 {"success":true,"data":{"success":true,"taskId":xxx,...}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentDeleteConfirmData {

    /** 是否成功删除 */
    private Boolean success;

    /** 任务ID */
    private Long taskId;

    /** 操作类型 */
    private String action;

    /** 消息 */
    private String message;

    /** 是否已删除（兼容字段） */
    private Boolean deleted;
}
