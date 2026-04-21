package com.cq.panel.admin.server.config;

import com.cq.panel.admin.server.common.properties.PermitAllUrlProperties;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.repository.domain.SysRole;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import com.cq.panel.admin.server.web.service.TokenService;
import com.cq.panel.authlite.User;
import com.cq.panel.authlite.filter.AuthFilter;
import com.cq.panel.authlite.filter.TokenValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * 轻量级鉴权配置（无 spring-security）
 */
@Configuration
public class SecurityConfig
{
    @Bean
    public TokenValidator tokenValidator(TokenService tokenService)
    {
        return token -> {
            LoginUser loginUser = tokenService.getLoginUser(token);
            if (loginUser == null)
            {
                return null;
            }
            tokenService.verifyToken(loginUser);

            Set<String> roles = new HashSet<>();
            if (loginUser.getUser() != null && loginUser.getUser().getRoles() != null)
            {
                for (SysRole role : loginUser.getUser().getRoles())
                {
                    if (role != null && StringUtils.isNotEmpty(role.getRoleKey()))
                    {
                        roles.add(role.getRoleKey());
                    }
                }
            }
            
            // 确保超级管理员拥有admin角色
            if (loginUser.getUser() != null && loginUser.getUser().isAdmin()) {
                roles.add("admin");
            }
            
            Set<String> permissions = loginUser.getPermissions() == null ? Set.of() : loginUser.getPermissions();
            
            // 使用用户ID作为username，确保不为null
            String username = loginUser.getUser() != null && loginUser.getUser().getUserName() != null 
                ? loginUser.getUser().getUserName() 
                : "user_" + loginUser.getUserId();
            
            return new User(username, roles, permissions);
        };
    }

    @Bean
    public AuthFilter authFilter(TokenValidator tokenValidator, PermitAllUrlProperties permitAllUrl)
    {
        List<String> ignore = new ArrayList<>();
        if (permitAllUrl != null && permitAllUrl.getUrls() != null)
        {
            ignore.addAll(permitAllUrl.getUrls());
        }
        ignore.add("/login");
        ignore.add("/register");
        ignore.add("/captchaImage");
        ignore.add("/getSalt");
        ignore.add("/logout");
        ignore.add("/actuator/**");
        ignore.add("/admin/server/**");
        ignore.add("/agent/registry/register");
        ignore.add("/agent/registry/heartbeat");
        ignore.add("/test/user/**");
        ignore.add("/");
        ignore.add("/*.html");
        ignore.add("/**/*.html");
        ignore.add("/**/*.css");
        ignore.add("/**/*.js");
        ignore.add("/profile/**");
        ignore.add("/swagger-ui.html");
        ignore.add("/v3/api-docs/**");
        ignore.add("/swagger-ui/**");
        ignore.add("/druid/**");
        ignore.add("/doc.html");

        return new AuthFilter(tokenValidator, ignore);
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilterRegistration(AuthFilter authFilter)
    {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authFilter);
        registration.addUrlPatterns("/*");
        registration.setName("authFilter");
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE + 50);
        return registration;
    }
}