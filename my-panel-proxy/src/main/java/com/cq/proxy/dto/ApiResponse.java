package com.cq.proxy.dto;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

/**
 * 统一API响应DTO
 * 用于所有Controller方法的返回值，替代Map<String, Object>
 * 提供类型安全的响应结构
 */
@Data
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态码: 200=成功, 400=参数错误, 500=服务器错误 */
    private int code;

    /** 消息: success, error描述等 */
    private String msg;

    /** 业务数据 */
    private T data;

    public ApiResponse() {}

    public ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ==================== 工厂方法 ====================

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(200, msg, data);
    }

    /**
     * 参数错误（400）
     */
    public static <T> ApiResponse<T> badRequest(String msg) {
        return new ApiResponse<>(400, msg, null);
    }

    /**
     * 服务器错误（500）
     */
    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(500, msg, null);
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }
}
