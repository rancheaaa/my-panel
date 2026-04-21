# My-Panel

## 项目简介
My-Panel 是一个基于 React 19 和 Spring Boot 3 的现代化前后端分离后台管理系统。本项目参考了 RuoYi 的架构设计，旨在提供高效、稳定、易用的开发框架。

**核心特性：支持单机/集群双模式无缝切换。**
- **单机模式 (Standalone)**: 零依赖启动，使用 H2 嵌入式数据库 + Caffeine 本地缓存，适合开发、演示及小规模部署。
- **集群模式 (Cluster)**: 标准生产架构，使用 MySQL 数据库 + Redis 分布式缓存，支持高并发与横向扩展。

## 技术栈

### 前端 (my-panel-ui)
*   **开发框架**: React 19.2.0
*   **构建工具**: Vite 7.2.4
*   **UI 组件库**: Ant Design 6.2.2
*   **路由管理**: React Router DOM 7.13.0
*   **HTTP 客户端**: Axios 1.13.4
*   **CSS 预处理**: Sass (SCSS)
*   **日期处理**: Day.js
*   **动画库**: Framer Motion
*   **图标库**: @ant-design/icons

### 后端 (my-panel-admin)
*   **开发语言**: Java 21
*   **核心框架**: Spring Boot 3.5.10
*   **数据库**:
    *   **Cluster**: MySQL 8.2.0 (生产环境推荐)
    *   **Standalone**: H2 Database (嵌入式，开发环境默认)
*   **持久层框架**: MyBatis 3.0.3 + PageHelper
*   **数据库连接池**: Druid 1.2.23
*   **缓存中间件**:
    *   **Cluster**: Redis (分布式缓存)
    *   **Standalone**: Caffeine (本地高性能缓存)
*   **安全认证**: JWT (JSON Web Token) + Spring Security
*   **API 文档**: SpringDoc (Swagger 3)
*   **工具库**: FastJson2, POI, Oshi, UserAgentUtils

## 目录结构

```
my-panel/
├── my-panel-admin/         # 后端工程
│   ├── my-panel-admin-server/  # 核心服务模块
│   ├── distribution/           # 发行版打包模块 (包含启动脚本、Nginx、JDK等)
│   └── pom.xml                 # Maven 依赖配置
├── my-panel-ui/            # 前端工程
│   ├── src/                    # 源代码
│   ├── public/                 # 静态资源
│   ├── package.json            # NPM 依赖配置
│   └── vite.config.js          # Vite 配置
└── README.md               # 项目说明文档
```

## 快速开始

### 后端启动

#### 方式一：单机模式（推荐开发使用）
无需安装 MySQL 和 Redis，直接启动即可。
1.  导入 `my-panel-admin` 到 IDE 中。
2.  确保 `application.yml` 中 `app.mode` 设置为 `standalone` (默认)。
3.  运行 `my-panel-admin-server` 下的主程序启动服务。
    *   数据将存储在项目根目录下的 H2 数据库文件中。

#### 方式二：集群模式（生产环境）
1.  确保已安装 JDK 21, MySQL 8+, Redis。
2.  导入 `my-panel-admin` 到 IDE 中。
3.  修改 `application.yml` 中 `app.mode` 设置为 `cluster`。
4.  在 `application-cluster.yml` 中配置您的 MySQL 和 Redis 连接信息。
5.  运行 `my-panel-admin-server` 下的主程序启动服务。

## 项目构建与打包

项目提供了一键打包功能，会将前端 UI 自动构建并集成到后端发行版中，同时自动下载必要的 JDK 和 Nginx 运行环境。

### 1. 准备工作
- 确保本地已安装 **Maven 3.6+**。
- 确保本地已安装 **Node.js 18+**。
- 确保网络畅通（用于自动下载前端依赖及后端运行环境）。

### 2. 打包命令
在项目根目录下执行以下命令：

```bash
mvn clean package -DskipTests
```

### 3. 安装包说明
打包成功后，发行版安装包将生成在以下位置：
`my-panel-admin/distribution/target/my-panel-1.0.0.zip` (或 `.tar.gz`)

解压安装包后，目录结构如下：
- `bin/`: 包含服务启动脚本 (`my-panel-admin.sh` / `.bat`)。
- `config/`: 外部配置文件目录。
- `libs/`: 包含 Fat JAR 主程序。
- `jdk/`: 自动下载的 Java 运行环境。
- `nginx/`: 自动下载的 Nginx 服务。
- `pages/`: 构建完成的前端静态资源。
- `logs/`: 默认日志输出目录。

### 4. 部署启动
1. 将打包好的压缩包上传至服务器并解压。
2. 进入 `bin` 目录。
3. **环境安装**（首次部署执行）：
   - **Linux**: `./my-panel-admin.sh install`
   - **Windows**: `my-panel-admin.bat install`
   *此步骤将自动解压 JDK 和 Nginx，并完成基础环境配置。*
4. **启动服务**：
   - **Linux**: `./my-panel-admin.sh start`
   - **Windows**: `my-panel-admin.bat start`
5. 访问启动后的服务地址（默认为 80 端口或配置端口）。

## 其他说明

### 关于自动下载
为减小源码库体积，JDK 和 Nginx 的安装包不会提交到 Git。在打包过程中，Maven 会根据 `distribution/pom.xml` 中的配置自动下载缺失的二进制包。

### 进程管理
启动脚本支持对单个进程进行操作：
- 仅操作 Java 服务：`./my-panel-admin.sh app start`
- 仅操作 Nginx 服务：`./my-panel-admin.sh nginx start`
- 同时操作所有服务：`./my-panel-admin.sh start`

### 默认账号密码
系统初始化了以下三个测试账号：

| 用户名 | 原始密码 | 说明 |
|--------|----------|------|
| admin | admin123 | 超级管理员，拥有所有权限 |
| guest | guest123 | 来宾账号，仅有查看权限 |
| cq | cq123456 | 普通账号，默认角色权限 |

> **注意**：首次登录后请及时修改密码。密码采用双重加密传输（前端 MD5+Salt，后端 BCrypt）。

## 许可证
[Apache License 2.0](LICENSE)
