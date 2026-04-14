# sys_job 扩展功能操作手册

## 1. 功能概述
本次扩展为定时任务模块新增了两种调度模式支持：
- **内置方法调度**：通过反射直接调用 Spring 容器中的 Bean 方法或类静态方法。
- **HTTP 接口调度**：通过 RestTemplate 发起外部 HTTP 请求（GET/POST/PUT/DELETE）。

## 2. 数据库升级
执行以下 SQL 脚本以完成表结构扩展：

```sql
ALTER TABLE `sys_job` 
ADD COLUMN `job_type` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1-内置方法 2-HTTP接口' AFTER `invoke_target`,
ADD COLUMN `method_name` varchar(255) DEFAULT NULL COMMENT '内置方法全限定名' AFTER `job_type`,
ADD COLUMN `http_url` varchar(500) DEFAULT NULL COMMENT 'HTTP接口URL' AFTER `method_name`,
ADD COLUMN `http_method` varchar(10) DEFAULT NULL COMMENT 'HTTP请求方式' AFTER `http_url`,
ADD COLUMN `http_headers` text DEFAULT NULL COMMENT 'HTTP请求头(JSON)' AFTER `http_method`,
ADD COLUMN `http_body` text DEFAULT NULL COMMENT 'HTTP请求体' AFTER `http_headers`;

ALTER TABLE `sys_job` ADD CONSTRAINT `check_job_type_fields` CHECK (
    (job_type = 1 AND method_name IS NOT NULL AND http_url IS NULL) OR
    (job_type = 2 AND http_url IS NOT NULL AND method_name IS NULL)
);
```

## 3. 配置示例

### 3.1 内置方法调度
- **任务类型**：内置方法
- **内置方法**：`com.cq.panel.admin.server.task.AppTask.backupSystemAllTable`
- **说明**：方法必须为无参且返回 void。系统会优先从 Spring 容器获取 Bean，若获取不到则尝试通过反射实例化。

### 3.2 HTTP 接口调度
- **任务类型**：HTTP接口
- **接口 URL**：`http://api.example.com/tasks/{jobId}`
- **请求方式**：`POST`
- **请求头**：`{"Content-Type": "application/json", "Authorization": "Bearer token"}`
- **请求体**：`{"name": "{jobName}", "status": "running"}`
- **说明**：
    - URL 和请求体支持占位符：`{jobId}`, `{jobName}`, `{jobGroup}`。
    - 超时时间固定为 30 秒。
    - 响应状态码 >= 400 会被记录为执行失败。

## 4. 常见错误码对照表

| 错误信息 | 原因分析 | 解决方案 |
| :--- | :--- | :--- |
| 内置方法格式错误 | `method_name` 不包含点号或类名不完整 | 检查并输入全限定名，如 `com.pkg.Class.method` |
| HTTP 接口调用失败，状态码：404 | 目标接口地址不存在 | 检查 `http_url` 是否正确，或目标服务是否在线 |
| HTTP 接口调用失败，状态码：500 | 目标接口内部异常 | 检查目标服务的业务逻辑和日志 |
| 请输入合法的 JSON 格式 | `http_headers` 输入了非 JSON 字符串 | 使用标准的 JSON 格式，如 `{"Key": "Value"}` |
| Cron表达式不正确 | 输入的 Cron 字符串不符合 Quartz 规范 | 使用 Cron 生成器或参考标准表达式 |

## 5. 开发验证
- **后端测试**：执行 `com.cq.panel.admin.server.common.utils.quartz.JobInvokeUtilTest`。
- **前端验证**：在“定时任务”页面尝试新增不同类型的任务并点击“执行”按钮验证。
