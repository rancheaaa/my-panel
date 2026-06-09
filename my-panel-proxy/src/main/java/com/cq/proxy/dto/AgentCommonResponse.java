package com.cq.proxy.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Agent通用API响应包装
 * 对应 {"success":true,"msg":"ok","data":T}
 *
 * @param <T>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentCommonResponse<T> {

    private Boolean success;
    private String msg;
    private T data;
}
