package com.cq.panel.admin.server.web.exception.user;

import com.cq.panel.admin.server.web.exception.base.BaseException;

import java.io.Serial;

/**
 * 用户信息异常类
 * 
 * @author cq
 */
public class UserException extends BaseException
{
    @Serial
    private static final long serialVersionUID = 1L;

    public UserException(String code, Object[] args)
    {
        super("user", code, args, null);
    }
}
