package com.cq.panel.admin.server.web.service;

import com.cq.panel.admin.server.common.enums.UserStatus;
import com.cq.panel.admin.server.common.utils.MessageUtils;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.repository.service.ISysUserService;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import com.cq.panel.admin.server.web.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginUserService {
    private static final Logger log = LoggerFactory.getLogger(LoginUserService.class);

    @Autowired
    private ISysUserService userService;

    @Autowired
    private SysPasswordService passwordService;

    @Autowired
    private SysPermissionService permissionService;

    public LoginUser loadLoginUserByUsername(String username) {
        SysUser user = userService.selectUserByUserName(username);
        if (MyStringUtils.isNull(user)) {
            log.info("登录用户：{} 不存在.", username);
            throw new ServiceException(MessageUtils.message("user.not.exists"));
        } else if (UserStatus.DELETED.getCode().equals(user.getDelFlag())) {
            log.info("登录用户：{} 已被删除.", username);
            throw new ServiceException(MessageUtils.message("user.password.delete"));
        } else if (UserStatus.DISABLE.getCode().equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", username);
            throw new ServiceException(MessageUtils.message("user.blocked"));
        }

        passwordService.validate(user);
        return new LoginUser(user.getUserId(), user.getDeptId(), user, permissionService.getMenuPermission(user));
    }
}

