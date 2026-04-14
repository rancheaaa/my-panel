# URL 白名单对齐校验（diff）

## 1. 目标

- 确保新鉴权框架的 URL 白名单与历史 Spring Security 行为逐条对齐
- 本次对齐基准来源：当前 [SecurityConfig.java](file:///e:/java-project2/my-panel/my-panel-admin/src/main/java/com/cq/panel/admin/server/config/SecurityConfig.java)

## 2. 白名单清单（最终生效）

- 动态：`permitAllUrl.getUrls()`
- 静态：
  - `/login`
  - `/register`
  - `/captchaImage`
  - `/logout`
  - `/actuator/**`
  - `/admin/server/**`
  - `/agent/registry/register`
  - `/agent/registry/heartbeat`
  - `/`
  - `/*.html`
  - `/**/*.html`
  - `/**/*.css`
  - `/**/*.js`
  - `/profile/**`
  - `/swagger-ui.html`
  - `/v3/api-docs/**`
  - `/swagger-ui/**`
  - `/druid/**`
  - `/doc.html`

## 3. diff 结论

- 上述白名单与《现有鉴权点清单》的白名单描述一致
  - [现有鉴权点清单.md](file:///e:/java-project2/my-panel/my-panel-admin/docs/security-migration/现有鉴权点清单.md)

