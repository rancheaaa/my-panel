# 性能测试报告（JMeter）

本目录提供可复现的 JMeter 压测脚本与报告模板，用于验证：

- 单实例 5000+ 并发连接
- QPS 10000+
- P99 < 100ms
- 内存 < 2GB

## 压测脚本

`proxy-api.jmx`：包含以下接口场景：

- `GET /api/health`
- `GET /api/v1/registry/discover`
- `GET /api/v1/config/{configKey}`

## 运行方式

```bash
jmeter -n -t proxy-api.jmx -l result.jtl -e -o report
```

## 记录项（填写）

- 目标环境：CPU/内存/OS/网络
- JVM 参数：`-Xms2g -Xmx2g -XX:+UseG1GC`
- 并发数：
- Ramp-up：
- 持续时间：

## 结果摘要（填写）

- QPS：
- P50/P90/P99：
- 错误率：
- GC 情况：
- CPU/内存峰值：

