package com.cq.proxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Agent通用API响应包装
 * 对应 {"success":true,"code":200,"msg":"ok","data":T}
 *
 * @param <T>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentCommonResponse<T> {

    private Boolean success;
    private Integer code;
    private String msg;
    private T data;
}
