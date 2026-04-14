package com.cq.agent.dto;

import lombok.Data;

/**
 * Proxy服务端返回的API响应格式
 * 与proxy的ApiResponse格式匹配
 * 
 * @author cq
 */

@Data
public class ProxyApiResponse<T> {
    
    private int code;
    private String message;
    private T data;


    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return code == 200;
    }
}