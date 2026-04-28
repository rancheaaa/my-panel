# Spring Boot Actuator 监控文档

本项目集成了 Spring Boot Actuator，用于提供企业级的应用监控和管理功能。

## 1. 简介

Spring Boot Actuator 提供了生产级别的功能，可以帮助你监控和管理应用程序。你可以通过 HTTP 端点或 JMX 来审计、健康检查和收集指标。

## 2. 访问地址

监控端点基础路径：`/actuator`

| 端点 | 描述 | 访问 URL |
| :--- | :--- | :--- |
| **health** | 显示应用程序的运行状况信息 | `/actuator/health` |
| **info** | 显示任意的应用信息 | `/actuator/info` |
| **metrics** | 展示当前应用的指标信息 | `/actuator/metrics` |
| **loggers** | 显示和修改配置的日志记录器 | `/actuator/loggers` |
| **threaddump** | 执行线程转储 | `/actuator/threaddump` |
| **heapdump** | 返回堆转储文件 (hprof) | `/actuator/heapdump` |
| **beans** | 显示应用程序中所有 Spring Bean 的完整列表 | `/actuator/beans` |
| **mappings** | 显示所有 @RequestMapping 路径的整理列表 | `/actuator/mappings` |
| **env** | 显示 Spring Environment 属性 | `/actuator/env` |
| **configprops** | 显示所有 @ConfigurationProperties 的整理列表 | `/actuator/configprops` |

## 3. 常用端点详解

### 3.1 健康检查 (Health)
URL: `/actuator/health`

返回系统的健康状态，包括磁盘空间、数据库连接、Redis 连接等组件的状态。
配置中已开启详细信息显示 (`show-details: always`)。

**示例响应:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 1099511627776,
        "free": 499511627776,
        "threshold": 10485760,
        "exists": true
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "5.0.14"
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### 3.2 指标监控 (Metrics)
URL: `/actuator/metrics`

列出所有可用的指标名称。要查看特定指标的详细信息，请在 URL 后附加指标名称，例如 `/actuator/metrics/jvm.memory.used`。

**常用指标:**
- `jvm.memory.used`: JVM 内存使用情况
- `jvm.gc.pause`: GC 暂停时间
- `http.server.requests`: HTTP 请求统计
- `system.cpu.usage`: 系统 CPU 使用率

### 3.3 日志管理 (Loggers)
URL: `/actuator/loggers`

查看当前系统的日志级别配置。
支持通过 POST 请求动态修改日志级别，无需重启服务。

**修改日志级别示例:**
发送 POST 请求到 `/actuator/loggers/com.cq.panel`
```json
{
  "configuredLevel": "DEBUG"
}
```

## 4. 安全说明

在 `SecurityConfig` 中，`/actuator/**` 路径已被配置为允许匿名访问 (`permitAll()`) 以便于演示和调试。

**生产环境建议:**
在生产环境中，强烈建议对 Actuator 端点进行安全保护，避免敏感信息泄露。
1. 使用 Spring Security 限制访问权限 (例如仅允许 `ADMIN` 角色)。
2. 使用独立的管理端口 (配置 `management.server.port`) 并配合防火墙策略。

## 5. 集成 Prometheus (可选)

如果需要集成 Prometheus + Grafana 监控体系，只需引入 `micrometer-registry-prometheus` 依赖，Actuator 会自动暴露 `/actuator/prometheus` 端点供 Prometheus 拉取数据。

## 6. 服务监控大屏 API（新增）

### 6.1 接口列表

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/monitor/dashboard/data` | 兼容接口：执行一次采集并返回当前概览 |
| GET | `/monitor/dashboard/overview` | 返回最近一次采样的完整分类指标和告警概览 |
| POST | `/monitor/dashboard/trend` | 按分类/指标/时间范围/粒度查询历史趋势 |
| GET | `/monitor/dashboard/alert/rules` | 查询告警规则 |
| POST | `/monitor/dashboard/alert/rule` | 新增或更新告警规则 |
| DELETE | `/monitor/dashboard/alert/rule/{id}` | 删除告警规则 |
| GET | `/monitor/dashboard/alert/events` | 查询告警事件 |

### 6.2 趋势查询参数

请求体：`/monitor/dashboard/trend`

```json
{
  "category": "cpu",
  "metricNames": ["cpu_usage_pct", "cpu_process_usage_pct"],
  "granularity": "1m",
  "beginTime": "2026-04-24 14:00:00",
  "endTime": "2026-04-24 15:00:00",
  "limit": 2000
}
```

- `category` 支持：`heap`、`gc`、`thread`、`cpu`、`system_load`、`db_pool`
- `granularity` 支持：`1m`、`5m`、`15m`、`1h`、`nh`、`1d`、`nd`、`7d`
- `limit` 默认受系统参数 `sys.monitor.maxPoints` 控制

### 6.3 系统参数

- `sys.monitor.collectIntervalMs`：采集间隔毫秒，默认 `10000`
- `sys.monitor.retentionDays`：历史保留天数，默认 `7`
- `sys.monitor.maxPoints`：单次趋势查询最大点数，默认 `2000`

### 6.4 数据表

- `monitor_metric_sample`：监控指标采样明细（支持按时间与指标多维查询）
- `monitor_alert_rule`：告警规则
- `monitor_alert_event`：告警事件

历史清理由定时任务自动执行，避免监控数据无限增长。
