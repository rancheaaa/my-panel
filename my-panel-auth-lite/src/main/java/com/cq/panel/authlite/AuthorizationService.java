package com.cq.panel.authlite;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class AuthorizationService {

    public boolean hasRole(String role) {
        User user = AuthContext.getCurrentUser();
        return user != null && user.getRoles().contains(role);
    }

    public boolean hasPermission(String permission) {
        User user = AuthContext.getCurrentUser();
        if (user == null) {
            return false;
        }
        
        Set<String> userPermissions = user.getPermissions();
        
        // 直接匹配权限
        if (userPermissions.contains(permission)) {
            return true;
        }
        
        // 通配符匹配支持
        for (String userPermission : userPermissions) {
            if (matchesWithWildcard(userPermission, permission)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查权限是否匹配，支持通配符 *
     * 例如：
     * - "*:*:query" 匹配 "user:add:query"、"role:delete:query" 等
     * - "user:*:query" 匹配 "user:add:query"、"user:delete:query" 等
     * - "user:add:*" 匹配 "user:add:query"、"user:add:update" 等
     * 
     * @param pattern 包含通配符的模式
     * @param permission 要检查的权限字符串
     * @return 是否匹配
     */
    private boolean matchesWithWildcard(String pattern, String permission) {
        // 如果模式不包含通配符，直接比较
        if (!pattern.contains("*")) {
            return pattern.equals(permission);
        }
        
        // 将通配符模式转换为正则表达式
        String regex = pattern
            .replace(".", "\\.")  // 转义点号
            .replace("*", ".*");    // 将*转换为.*
        
        // 确保匹配整个字符串
        regex = "^" + regex + "$";
        
        try {
            return Pattern.matches(regex, permission);
        } catch (Exception e) {
            // 如果正则表达式有误，回退到简单匹配
            return pattern.equals(permission);
        }
    }
    
    /**
     * 检查是否拥有任意一个权限
     * @param permissions 权限列表
     * @return 是否拥有任意一个权限
     */
    public boolean hasAnyPermission(List<String> permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否拥有所有权限
     * @param permissions 权限列表
     * @return 是否拥有所有权限
     */
    public boolean hasAllPermissions(List<String> permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
}