package com.cq.proxy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiResponse", description = "统一返回格式")
public record ApiResponse<T>(
    @Schema(description = "业务码") int code,
    @Schema(description = "消息") String message,
    @Schema(description = "数据") T data
) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(200, "OK", data);
  }

  public static <T> ApiResponse<T> ok(String message, T data) {
    return new ApiResponse<>(200, message, data);
  }

  public static <T> ApiResponse<T> fail(int code, String message) {
    return new ApiResponse<>(code, message, null);
  }
}

