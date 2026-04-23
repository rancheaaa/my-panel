package com.cq.panel.admin.server.web.service;

import java.util.concurrent.TimeUnit;
import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.web.service.cache.CacheService;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.exception.user.UserPasswordNotMatchException;
import com.cq.panel.admin.server.web.exception.user.UserPasswordRetryLimitExceedException;
import com.cq.panel.admin.server.common.utils.Md5PasswordEncoder;
import com.cq.panel.admin.server.context.AuthenticationContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.cq.panel.authlite.AuthenticationToken;

/**
 * 登录密码方法
 * 
 * @author cq
 */
@Component
public class SysPasswordService
{
    @Autowired
    private CacheService cacheService;

    @Value(value = "${user.password.maxRetryCount}")
    private int maxRetryCount;

    @Value(value = "${user.password.lockTime}")
    private int lockTime;

    /**
     * 登录账户密码错误次数缓存键名
     * 
     * @param username 用户名
     * @return 缓存键key
     */
    private String getCacheKey(String username)
    {
        return CacheConstants.PWD_ERR_CNT_KEY + ":" + username;
    }

    public void validate(SysUser user)
    {
        AuthenticationToken usernamePasswordAuthenticationToken = AuthenticationContextHolder.getContext();
        String username = usernamePasswordAuthenticationToken.getPrincipal().toString();
        String password = usernamePasswordAuthenticationToken.getCredentials().toString();

        Integer retryCount = cacheService.get(getCacheKey(username));

        if (retryCount == null)
        {
            retryCount = 0;
        }

        if (retryCount >= maxRetryCount)
        {
            throw new UserPasswordRetryLimitExceedException(maxRetryCount, lockTime);
        }

        if (!matches(user, password))
        {
            retryCount = retryCount + 1;
            cacheService.set(getCacheKey(username), retryCount, lockTime, TimeUnit.MINUTES);
            throw new UserPasswordNotMatchException();
        }
        else
        {
            clearLoginRecordCache(username);
        }
    }

    public boolean matches(SysUser user, String rawPassword)
    {
        String salt = user.getSalt();
        if (salt == null || salt.isEmpty()) {
            throw new IllegalStateException("用户密码盐值为空");
        }
        return Md5PasswordEncoder.matches(rawPassword, user.getPassword());
    }

    public void clearLoginRecordCache(String loginName)
    {
        if (cacheService.hasKey(getCacheKey(loginName)))
        {
            cacheService.delete(getCacheKey(loginName));
        }
    }
}
