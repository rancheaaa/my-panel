package com.cq.panel.admin.server.web.domain.dto.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Agent通用响应包装
 * 对应Agent端API的标准响应格式：{"success":true,"msg":"ok","data":T}
 *
 * @param <T> data字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentResponse<T> {

    /** 是否成功 */
    private Boolean success;

    /** 消息 */
    private String msg;

    /** 数据 */
    private T data;
}
