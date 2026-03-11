package com.cq.panel.admin.server.web.exception.user;

/**
 * 验证码失效异常类
 * 
 * @author cq
 */
public class CaptchaExpireException extends UserException
{
    private static final long serialVersionUID = 1L;

    public CaptchaExpireException()
    {
        super("user.jcaptcha.expire", null);
    }
}
