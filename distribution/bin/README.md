# My-Panel 管理脚本使用指南

本目录包含用于管理 My-Panel-Admin 服务的核心脚本。通过这些脚本，您可以轻松完成环境安装、服务启动、停止及状态检查。

## 1. 脚本说明

- **Windows**: `my-panel-admin.bat`
- **Linux**: `my-panel-admin.sh`

## 2. 快速入门

### 首次安装
直接运行脚本（不带参数）或指定 `install` 参数，脚本会自动检查并安装所需的 JDK 21 和 Nginx。

```bash
# Windows
my-panel-admin.bat install

# Linux
./my-panel-admin.sh install
```

### 启动服务
在确保依赖已安装后，执行 `start` 命令。您可以选择启动全部服务，或仅启动特定服务：

```bash
# Windows
my-panel-admin.bat start          # 启动所有服务
my-panel-admin.bat app start      # 仅启动 Java 后端
my-panel-admin.bat nginx start    # 仅启动 Nginx

# Linux
./my-panel-admin.sh start         # 启动所有服务
./my-panel-admin.sh app start     # 仅启动 Java 后端
./my-panel-admin.sh nginx start   # 仅启动 Nginx
```

## 3. 命令详解

您可以选择在命令前添加 `app` 或 `nginx` 前缀，以实现对特定服务的精准控制。如果不添加前缀，则默认对所有服务（Java + Nginx）进行操作。

| 命令 | 描述 | 示例 |
| :--- | :--- | :--- |
| `install` | 检查系统环境，安装/解压 JDK 21 和 Nginx。 | `app install` |
| `start` | 启动后端服务或 Nginx 服务。 | `nginx start` |
| `stop` | 停止后端服务或 Nginx 服务。 | `app stop` |
| `restart` | 重启指定或全部服务。 | `restart` |
| `status` | 查看服务运行状态。 | `app status` |

## 4. 关键特性

- **前置检查 (Pre-check)**: 执行 `start/restart/stop/status` 时，如果未检测到安装环境，脚本会提示并引导您先执行 `install`。
- **配置持久化**: 首次安装时会生成默认 `nginx.conf`。之后脚本**不会**覆盖该文件，您可以放心地手动修改 Nginx 配置。
- **离线支持 (Linux)**: 在源码编译 Nginx 时，如果机器无法联网，脚本会输出详细的依赖包下载及安装指引。
- **生产级 Nginx**: Linux 下编译的 Nginx 包含 `HTTP/2`、`SSL`、`Stream`、`RealIP` 等生产环境常用模块。

## 5. 注意事项

1. **权限 (Linux)**: 如果 Nginx 监听 80 或 443 端口，运行脚本时需要 `sudo` 权限。脚本默认使用 **8888** 端口以避免权限问题。
2. **配置文件**: 修改端口等核心配置请前往 `config/application.yml`，Nginx 的高级配置请直接修改 `nginx/conf/nginx.conf`。
3. **日志**: 服务的运行日志位于根目录的 `logs/` 文件夹下。
