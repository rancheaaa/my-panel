package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.config.AppConfig;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.file.FileUploadUtils;
import com.cq.panel.admin.server.common.utils.file.MimeTypeUtils;
import com.cq.panel.admin.server.repository.service.ISysUserService;
import com.cq.panel.admin.server.web.service.TokenService;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.UserProfileVO;
import com.cq.panel.admin.server.web.domain.vo.system.UserAvatarUpdateVO;
import com.cq.panel.admin.server.web.domain.dto.system.UserProfileUpdateDTO;
import com.cq.panel.admin.server.web.domain.dto.system.UserPwdUpdateDTO;
import com.cq.panel.admin.server.web.converter.system.SysUserConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人信息 业务处理
 * 
 * @author cq
 */
@Tag(name = "个人信息", description = "用户个人信息管理接口")
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController
{
    private final ISysUserService userService;

    private final TokenService tokenService;

    private final AppConfig appConfig;

    private final SysUserConverter userConverter;

    public SysProfileController(ISysUserService userService, TokenService tokenService, AppConfig appConfig, SysUserConverter userConverter) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.appConfig = appConfig;
        this.userConverter = userConverter;
    }

    /**
     * 个人信息
     */
    @Operation(summary = "个人信息", description = "获取当前登录用户的个人信息")
    @GetMapping
    public Result<UserProfileVO> profile()
    {
        LoginUser loginUser = getLoginUser();
        SysUser user = loginUser.getUser();
        UserProfileVO vo = new UserProfileVO();
        vo.setUser(userConverter.toVO(user));
        vo.setRoleGroup(userService.selectUserRoleGroup(loginUser.getUsername()));
        vo.setPostGroup(userService.selectUserPostGroup(loginUser.getUsername()));
        return Result.success(vo);
    }

    /**
     * 修改用户
     */
    @Operation(summary = "修改用户", description = "修改用户个人信息")
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> updateProfile(@Validated @RequestBody UserProfileUpdateDTO dto)
    {
        LoginUser loginUser = getLoginUser();
        SysUser currentUser = loginUser.getUser();
        userConverter.updateEntity(currentUser, dto);
        if (StringUtils.isNotEmpty(dto.getPhonenumber()) && !userService.checkPhoneUnique(currentUser))
        {
            return Result.error("修改用户'" + loginUser.getUsername() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(dto.getEmail()) && !userService.checkEmailUnique(currentUser))
        {
            return Result.error("修改用户'" + loginUser.getUsername() + "'失败，邮箱账号已存在");
        }
        if (userService.updateUserProfile(currentUser) > 0)
        {
            // 更新缓存用户信息
            tokenService.setLoginUser(loginUser);
            return Result.success();
        }
        return Result.error("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     */
    @Operation(summary = "重置密码", description = "修改用户登录密码")
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/updatePwd")
    public Result<Void> updatePwd(@Validated @RequestBody UserPwdUpdateDTO dto)
    {
        LoginUser loginUser = getLoginUser();
        String userName = loginUser.getUsername();
        String password = loginUser.getPassword();
        if (!SecurityUtils.matchesPassword(dto.getOldPassword(), password))
        {
            return Result.error("修改密码失败，旧密码错误");
        }
        if (SecurityUtils.matchesPassword(dto.getNewPassword(), password))
        {
            return Result.error("新密码不能与旧密码相同");
        }
        String newPassword = SecurityUtils.encryptPassword(dto.getNewPassword());
        if (userService.resetUserPwd(userName, newPassword) > 0)
        {
            // 更新缓存用户密码
            loginUser.getUser().setPassword(newPassword);
            tokenService.setLoginUser(loginUser);
            return Result.success();
        }
        return Result.error("修改密码异常，请联系管理员");
    }

    /**
     * 头像上传
     */
    @Operation(summary = "头像上传", description = "上传用户头像图片")
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public Result<UserAvatarUpdateVO> avatar(@Parameter(description = "头像文件", required = true) @RequestParam("avatarfile") MultipartFile file) throws Exception
    {
        if (!file.isEmpty())
        {
            LoginUser loginUser = getLoginUser();
            String avatar = FileUploadUtils.upload(appConfig.getAvatarPath(), file, MimeTypeUtils.IMAGE_EXTENSION);
            if (userService.updateUserAvatar(loginUser.getUsername(), avatar))
            {
                // 更新缓存用户头像
                loginUser.getUser().setAvatar(avatar);
                tokenService.setLoginUser(loginUser);
                return Result.success(new UserAvatarUpdateVO(avatar));
            }
        }
        return Result.error("上传图片异常，请联系管理员");
    }
}






