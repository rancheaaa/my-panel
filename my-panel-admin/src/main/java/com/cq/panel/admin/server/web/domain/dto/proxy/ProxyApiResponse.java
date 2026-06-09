package com.cq.panel.admin.server.web.domain.dto.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Proxy API统一响应包装
 * 对应Proxy端ApiResponse的格式：{"code":200,"msg":"success","data":T}
 *
 * @param <T> data字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxyApiResponse<T> {

    /** 响应码，200表示成功 */
    private int code;

    /** 响应消息 */
    private String msg;

    /** 响应数据 */
    private T data;

    /**
     * 判断响应是否成功
     */
    public boolean isSuccess() {
        return code == 200;
    }
}
