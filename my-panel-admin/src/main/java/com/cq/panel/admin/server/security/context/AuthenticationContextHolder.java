package com.cq.panel.admin.server.security.context;

import com.cq.panel.authlite.AuthenticationToken;

/**
 * 身份验证信息
 * 
 * @author cq
 */
public class AuthenticationContextHolder
{
    private static final ThreadLocal<AuthenticationToken> contextHolder = new ThreadLocal<>();

    public static AuthenticationToken getContext()
    {
        return contextHolder.get();
    }

    public static void setContext(AuthenticationToken context)
    {
        contextHolder.set(context);
    }

    public static void clearContext()
    {
        contextHolder.remove();
    }
}
