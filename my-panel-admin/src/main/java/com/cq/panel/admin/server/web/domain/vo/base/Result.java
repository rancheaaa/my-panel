package com.cq.panel.admin.server.web.domain.vo.base;

import com.cq.panel.admin.server.common.constant.HttpStatus;
import java.io.Serial;
import java.io.Serializable;

/**
 * 操作消息提醒
 * 
 * @author cq
 */
public record Result<T>(int code, String msg, T data) implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 初始化一个新创建的 Result 对象
     */
    public Result(int code, String msg)
    {
        this(code, msg, null);
    }

    /**
     * 返回成功消息
     */
    public static <T> Result<T> success()
    {
        return Result.success("操作成功", null);
    }

    /**
     * 返回成功数据
     */
    public static <T> Result<T> success(T data)
    {
        return Result.success("操作成功", data);
    }

    /**
     * 返回成功消息
     */
    public static <T> Result<T> success(String msg, T data)
    {
        return new Result<>(HttpStatus.SUCCESS, msg, data);
    }

    /**
     * 返回警告消息
     */
    public static <T> Result<T> warn(String msg)
    {
        return Result.warn(msg, null);
    }

    /**
     * 返回警告消息
     */
    public static <T> Result<T> warn(String msg, T data)
    {
        return new Result<>(HttpStatus.WARN, msg, data);
    }

    /**
     * 返回错误消息
     */
    public static <T> Result<T> error()
    {
        return Result.error("操作失败");
    }

    /**
     * 返回错误消息
     */
    public static <T> Result<T> error(String msg)
    {
        return Result.error(msg, null);
    }

    /**
     * 返回错误消息
     */
    public static <T> Result<T> error(String msg, T data)
    {
        return new Result<>(HttpStatus.ERROR, msg, data);
    }

    /**
     * 返回错误消息
     */
    public static <T> Result<T> error(int code, String msg)
    {
        return new Result<>(code, msg, null);
    }
}

