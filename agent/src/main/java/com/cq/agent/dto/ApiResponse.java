package com.cq.agent.dto;

/**
 * Generic API response: success flag + code + msg + data.
 */
public class ApiResponse<T> {

    private boolean success;
    private int code;
    private String msg;
    private T data;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(true);
        r.setCode(ApiCode.SUCCESS.getCode());
        r.setMsg(ApiCode.SUCCESS.getDefaultMsg());
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> failure(int code, String msg) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static <T> ApiResponse<T> failure(String msg) {
        return failure(ApiCode.GENERIC_ERROR.getCode(), msg);
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "success=" + success +
                ", code=" + code +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                '}';
    }
}
