package com.cq.panel.admin.server.web.service;

import com.cq.panel.admin.server.web.service.cache.CacheService;
import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import com.cq.panel.admin.server.web.exception.ServiceException;
import com.cq.panel.admin.server.web.exception.user.*;
import com.cq.panel.admin.server.common.utils.DateUtils;
import com.cq.panel.admin.server.common.utils.MessageUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.ip.IpUtils;
import com.cq.panel.admin.server.manager.AsyncManager;
import com.cq.panel.admin.server.manager.AsyncFactory;
import com.cq.panel.admin.server.context.AuthenticationContextHolder;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.cq.panel.authlite.AuthenticationToken;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import com.cq.panel.admin.server.repository.service.ISysUserService;


/**
 * 登录校验方法
 * 
 * @author cq
 */
@Component
public class SysLoginService
{
    @Autowired
    private TokenService tokenService;

    @Resource
    private LoginUserService loginUserService;

    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysUserService userService;

    /**
     * 登录验证
     * 
     * @param username 用户名
     * @param password 密码
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public String login(String username, String password, String code, String uuid)
    {
        // 验证码校验
        validateCaptcha(username, code, uuid);
        // 登录前置校验
        loginPreCheck(username, password);
        try
        {
            AuthenticationContextHolder.setContext(new AuthenticationToken(username, password, false));
            LoginUser loginUser = loginUserService.loadLoginUserByUsername(username);
            AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
            recordLoginInfo(loginUser.getUserId());
            return tokenService.createToken(loginUser);
        }
        catch (ServiceException e)
        {
            AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.LOGIN_FAIL, e.getMessage()));
            throw e;
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }
    }

    /**
     * 校验验证码
     * 
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public void validateCaptcha(String username, String code, String uuid)
    {
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + ":" + StringUtils.nvl(uuid, "");
            String captcha = cacheService.get(verifyKey);
            if (captcha == null)
            {
                AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire")));
                throw new CaptchaExpireException();
            }
            cacheService.delete(verifyKey);
            if (!code.equalsIgnoreCase(captcha))
            {
                AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error")));
                throw new CaptchaException();
            }
        }
    }

    /**
     * 登录前置校验
     * @param username 用户名
     * @param password 用户密码（已加密）
     */
    public void loginPreCheck(String username, String password)
    {
        // 用户名或密码为空 错误
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))
        {
            AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("not.null")));
            throw new UserNotExistsException();
        }
        // 用户名不在指定范围内 错误
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        // IP黑名单校验
        String blackStr = configService.selectConfigByKey("sys.login.blackIPList");
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("login.blocked")));
            throw new BlackListException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param userId 用户ID
     */
    public void recordLoginInfo(Long userId)
    {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setLoginIp(IpUtils.getIpAddr());
        sysUser.setLoginDate(DateUtils.getNowDate());
        userService.updateUserProfile(sysUser);
    }
}
