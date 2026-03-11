# My-Panel 运维工具指南

本目录包含用于维护 My-Panel 项目及其组件（如 Nginx）的专用运维脚本。

## 1. Nginx 运维工具 (`nginx-ops`)

`nginx-ops.bat` (Windows) 和 `nginx-ops.sh` (Linux) 是针对嵌入式 Nginx 的封装工具，支持更细粒度的控制。

### 常用命令

| 命令 | 描述 | 应用场景 |
| :--- | :--- | :--- |
| `start` | 启动 Nginx 服务。 | 手动启动 Nginx。 |
| `stop` | 强制停止所有 Nginx 进程。 | 需要彻底关闭 Nginx 时。 |
| `restart` | 重启 Nginx 服务。 | 强制刷新服务状态（会有短暂停机）。 |
| `status` | 检查 Nginx 运行状态。 | 确认 Nginx 是否正在运行，并查看当前监听端口和配置文件路径。 |
| **`reload`** | **不停机刷新配置**。 | 修改 `nginx.conf` 后，在不中断服务的情况下应用新配置。 |
| **`check`** | **配置语法检测** | 在应用配置前，检测 `nginx.conf` 是否存在语法错误。 |
| **`info`** | **查看详情** | 打印 Nginx 配置文件路径和当前监听的所有端口。 |

### 使用示例

**Linux 离线刷新配置:**
```bash
./nginx-ops.sh check   # 先检查配置是否正确
./nginx-ops.sh reload  # 确认无误后不停机刷新
```

**Windows 检查状态:**
```batch
nginx-ops.bat status
```

## 2. 健康检查工具 (`health-check.sh`)

仅限 Linux 环境，用于检测后端服务和 Nginx 的健康状况，并尝试自动恢复（如果适用）。

## 3. 注意事项

1. **工作目录**: 运维脚本会自动计算项目根目录，您可以在任何位置调用它们。
2. **配置路径**: 脚本默认操作 `/nginx/nginx-1.24.0/conf/nginx.conf`，请确保路径正确。
3. **前置逻辑**: `reload` 和 `start` 命令在执行前都会自动调用 `check` 逻辑，以防止因配置错误导致服务中断。
