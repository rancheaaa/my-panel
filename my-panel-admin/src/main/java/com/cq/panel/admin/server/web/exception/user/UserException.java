package com.cq.panel.admin.server.web.exception.user;

import com.cq.panel.admin.server.web.exception.base.BaseException;

/**
 * 用户信息异常类
 * 
 * @author cq
 */
public class UserException extends BaseException
{
    private static final long serialVersionUID = 1L;

    public UserException(String code, Object[] args)
    {
        super("user", code, args, null);
    }
}
