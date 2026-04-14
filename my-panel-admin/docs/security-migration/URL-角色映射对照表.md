# URL-角色映射对照表（my-panel-admin）

## 1. URL 级（当前鉴权框架）

来源：[SecurityConfig.java](file:///e:/java-project2/my-panel/my-panel-admin/src/main/java/com/cq/panel/admin/server/config/SecurityConfig.java#L96-L136)

- URL 级规则仅区分两类：
  - 白名单（允许匿名访问）
  - 需要登录（其余全部需要携带有效 Bearer Token）
- 当前配置与历史 Spring Security 行为保持一致：URL 级不做 `hasRole(...)` / `hasAuthority(...)` 之类的角色映射，因此“角色 -> URL”的直接映射为空。

## 2. 方法级（权限点）

来源：Controller 方法的 `@RequirePermission("...")`

- 本项目鉴权粒度为“权限点字符串”（如 `system:user:list`），并非 URL 级别角色匹配。
- URL 与权限点对应关系见：`docs/security-migration/url-permission.csv`

## 3. 角色与权限点的关系

角色与权限点的绑定关系来自数据库与菜单/角色授权逻辑（通过角色拥有的菜单权限集合生成权限点集合），典型入口：
- 权限计算服务：[SysPermissionService.java](file:///e:/java-project2/my-panel/my-panel-admin/src/main/java/com/cq/panel/admin/server/web/service/SysPermissionService.java)
- 权限点判断服务（SpEL 入口 `@ss`）：[PermissionService.java](file:///e:/java-project2/my-panel/my-panel-admin/src/main/java/com/cq/panel/admin/server/web/service/PermissionService.java)
