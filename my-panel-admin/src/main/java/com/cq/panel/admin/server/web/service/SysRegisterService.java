package com.cq.panel.admin.server.web.service;

import com.cq.panel.admin.server.web.service.cache.CacheService;
import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.dto.system.RegisterDTO;
import com.cq.panel.admin.server.web.exception.user.CaptchaException;
import com.cq.panel.admin.server.web.exception.user.CaptchaExpireException;
import com.cq.panel.admin.server.common.utils.MessageUtils;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.manager.AsyncManager;
import com.cq.panel.admin.server.manager.AsyncFactory;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import com.cq.panel.admin.server.repository.service.ISysUserService;
import org.springframework.stereotype.Component;


/**
 * 注册校验方法
 * 
 * @author cq
 */
@Component
public class SysRegisterService
{
    private final ISysUserService userService;

    private final ISysConfigService configService;

    private final CacheService cacheService;

    public SysRegisterService(ISysUserService userService, ISysConfigService configService, CacheService cacheService) {
        this.userService = userService;
        this.configService = configService;
        this.cacheService = cacheService;
    }

    /**
     * 注册
     */
    public String register(RegisterDTO registerDTO)
    {
        String msg = "", username = registerDTO.getUsername(), password = registerDTO.getPassword();
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);

        // 验证码开关
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            validateCaptcha(username, registerDTO.getCode(), registerDTO.getUuid());
        }

        if (StringUtils.isEmpty(username))
        {
            msg = "用户名不能为空";
        }
        else if (StringUtils.isEmpty(password))
        {
            msg = "用户密码不能为空";
        }
        else if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            msg = "账户长度必须在2到20个字符之间";
        }
        else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            msg = "密码长度必须在5到20个字符之间";
        }
        else if (!userService.checkUserNameUnique(sysUser))
        {
            msg = "保存用户'" + username + "'失败，注册账号已存在";
        }
        else
        {
            sysUser.setNickName(username);
            sysUser.setPassword(SecurityUtils.encryptPassword(password));
            boolean regFlag = userService.registerUser(sysUser);
            if (!regFlag)
            {
                msg = "注册失败,请联系系统管理人员";
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLoginInfo(username, Constants.REGISTER, MessageUtils.message("user.register.success")));
            }
        }
        return msg;
    }

    /**
     * 校验验证码
     * 
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     */
    @SuppressWarnings("all")
    public void validateCaptcha(String username, String code, String uuid)
    {
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
        String captcha = cacheService.get(verifyKey);
        cacheService.delete(verifyKey);
        if (captcha == null)
        {
            throw new CaptchaExpireException();
        }
        if (!code.equalsIgnoreCase(captcha))
        {
            throw new CaptchaException();
        }
    }
}

