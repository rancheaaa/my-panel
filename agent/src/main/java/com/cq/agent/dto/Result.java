package com.cq.agent.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * 操作消息提醒
 *
 * @author cq
 */
public class Result<T> implements Serializable {
    private final int code;
    private final String msg;
    private final T data;

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }

    public boolean isSuccess() {
        return code == 200;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Result<?> result = (Result<?>) o;
        return code == result.code && Objects.equals(msg, result.msg) && Objects.equals(data, result.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, msg, data);
    }

    @Override
    public String toString() {
        return "Result{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                '}';
    }
}

