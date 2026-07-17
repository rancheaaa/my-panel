================================================================================
                    My-Panel Docker 部署使用教程
================================================================================

一、项目简介
--------------------------------------------------------------------------------
My-Panel 是一个现代化的管理面板,基于 React 19 + Spring Boot 4 构建,
参考若依架构,支持双模式运行(Standalone/Cluster),并包含分布式 Agent 系统。

服务组件：
  1. MySQL 8.4.10      - 数据库（端口 5306）
  2. Redis 8.6.4       - 缓存（端口 7379）
  3. my-panel-admin    - 管理后台 REST API（端口 8888）
  4. my-panel-proxy1   - 注册中心 & 代理服务（端口 9876）
  5. my-panel-proxy2   - 注册中心 & 代理服务（端口 9877）
  6. nginx 1.27        - 前端控制台 + API 反向代理（端口 9999）


二、环境要求
--------------------------------------------------------------------------------
- Docker Engine 24.0+
- Docker Compose v2.20+
- 建议内存 4GB+
- 建议磁盘 20GB+

安装 Docker（CentOS）:
  yum install -y yum-utils
  yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
  yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
  systemctl start docker && systemctl enable docker

安装 Docker（Ubuntu）:
  apt-get update
  apt-get install -y docker.io docker-compose-plugin
  systemctl start docker && systemctl enable docker

推荐镜像配置
/etc/docker/daemon.json

{
  "registry-mirrors" : [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ],
  "insecure-registries" : [
    "docker.1ms.run"
  ],
  "debug": true,
  "experimental": false
}

验证安装:
  docker --version
  docker compose version


三、部署步骤
--------------------------------------------------------------------------------
1. 解压部署包到指定目录，例如/opt/my-panel
   tar -xf my-panel-1.0.0-docker.tar -C /opt/my-panel
   cd /opt/my-panel

2. 目录结构说明（解压后所有文件直接在当前目录）
   .
   ├── docker-compose.yml       # 容器编排配置
   ├── Dockerfile               # 镜像构建文件
   ├── my-panel.sh              # Docker 管理脚本（推荐使用）
   ├── my-panel-admin.jar       # 管理后台程序
   ├── my-panel-proxy.jar       # 代理服务程序
   ├── config/
   │   ├── admin/
   │   │   ├── application.yml          # 管理后台配置
   │   │   ├── application-standalone.yml  # Standalone 模式配置
   │   │   ├── application-cluster.yml     # Cluster 模式配置
   │   │   ├── common.properties       # 公共配置(数据库、Redis)
   │   │   ├── logback-spring.xml      # 管理后台日志配置
   │   │   └── banner.txt              # 启动 banner
   │   └── proxy/
   │       ├── application.properties  # 代理服务配置
   │       └── logback-spring.xml      # 代理服务日志配置
   ├── data                     # 数据文件以及日志文件
   ├── my-panel-ui/
   │   └── dist/                # 前端构建产物（nginx 静态资源）
   │       ├── index.html
   │       └── assets/
   ├── mysql/
   │   ├── my.cnf               # MySQL 配置
   │   └── init/
   │       ├── 00-init-mysql.sql   # MySQL 初始化脚本（root 远程授权）
   │       ├── 01-schema.sql       # 建表语句
   │       ├── 02-quartz.sql       # Quartz 定时任务表
   │       └── 03-data.sql         # 初始化数据（菜单、用户等）
   ├── nginx/
   │   └── default.conf         # nginx 站点配置（前端 + API 反向代理）
   └── README.txt               # 本文档

3. 修改配置（按需）
   - 数据库连接: 修改 docker-compose.yml 中 COMMON_DATASOURCE_URL
   - Redis 连接: 修改 docker-compose.yml 中 COMMON_REDIS_HOST/PORT
   - Proxy 地址: 修改 docker-compose.yml 中 PROXY_URLS
   - 端口冲突:   修改 docker-compose.yml 中 ports 映射
   - 服务器IP:   通过环境变量 HOST_IP 指定（默认自动检测）

4. 构建镜像并启动（推荐使用管理脚本）
   ./my-panel.sh up

   或直接使用 docker compose:
   docker compose up -d --build

5. 首次启动需要等待 30-60 秒（MySQL 初始化 + 服务启动）


四、管理脚本 my-panel.sh 使用说明
--------------------------------------------------------------------------------
管理脚本 my-panel.sh 封装了 docker compose 常用命令，提供一键式服务管理。
所有命令均在部署根目录执行。

查看帮助：
  ./my-panel.sh help

可用命令列表：

  up        构建镜像并启动所有服务（后台运行）
            示例: ./my-panel.sh up
                  ./my-panel.sh up mysql

  start     启动已停止的服务（不重新构建）
            示例: ./my-panel.sh start
                  ./my-panel.sh start mysql redis

  stop      停止服务（保留容器）
            示例: ./my-panel.sh stop
                  ./my-panel.sh stop admin proxy1

  restart   重启服务
            示例: ./my-panel.sh restart
                  ./my-panel.sh restart nginx

  down      停止并删除容器（保留数据卷）
            示例: ./my-panel.sh down

  down -v   停止并删除容器及数据卷（慎用！会丢失数据）
            示例: ./my-panel.sh down -v

  ps        查看所有服务状态
            示例: ./my-panel.sh ps

  logs      查看服务日志
            示例: ./my-panel.sh logs admin
                  ./my-panel.sh logs proxy1 --tail 100
                  ./my-panel.sh logs -f admin        (实时跟踪)

  build     重新构建镜像（不启动服务）
            示例: ./my-panel.sh build
                  ./my-panel.sh build admin

  shell     进入容器交互式终端
            示例: ./my-panel.sh shell admin
                  ./my-panel.sh shell mysql

  health    检查服务健康状态（彩色输出）
            示例: ./my-panel.sh health

  ip        显示服务器物理网卡IP地址
            示例: ./my-panel.sh ip

  stats     查看容器资源占用（CPU/内存/网络）
            示例: ./my-panel.sh stats

  config    查看当前 docker-compose.yml 配置
            示例: ./my-panel.sh config

  clean     清理停止的容器和未使用的镜像
            示例: ./my-panel.sh clean

  backup    备份 MySQL 数据库
            示例: ./my-panel.sh backup
                  ./my-panel.sh backup /tmp/my-panel-backup.sql

  restore   从 SQL 文件恢复 MySQL 数据库
            示例: ./my-panel.sh restore /tmp/my-panel-backup.sql

服务列表: mysql  redis  admin  proxy1  proxy2  nginx
说明: 不指定服务名时，命令作用于所有服务


五、Docker Compose 常用命令（底层命令）
--------------------------------------------------------------------------------
以下命令均在部署根目录（解压目录）执行。

# 启动所有服务（后台运行）
docker compose up -d --build

# 启动单个服务
docker compose up -d admin
docker compose up -d proxy1
docker compose up -d nginx

# 停止所有服务（保留容器）
docker compose stop

# 停止单个服务
docker compose stop admin

# 启动已停止的服务
docker compose start

# 停止并删除容器（保留数据卷）
docker compose down

# 停止并删除容器 + 删除数据卷（慎用！会丢失数据库数据）
docker compose down -v

# 重启所有服务
docker compose restart

# 重启单个服务
docker compose restart admin

# 重新构建镜像（代码更新后）
docker compose up -d --build admin

# 查看所有服务状态
docker compose ps

# 查看某个服务状态
docker compose ps admin


六、日志查看
--------------------------------------------------------------------------------

# 查看所有服务日志
docker compose logs

# 查看指定服务日志
docker compose logs admin
docker compose logs proxy1
docker compose logs proxy2
docker compose logs nginx
docker compose logs mysql
docker compose logs redis

# 实时跟踪日志（类似 tail -f）
docker compose logs -f admin

# 查看最近 100 行日志
docker compose logs --tail 100 admin

# 查看指定时间后的日志
docker compose logs --since "2025-01-01T00:00:00" admin

# 查看最近 5 分钟的日志
docker compose logs --since 5m admin


七、容器操作
--------------------------------------------------------------------------------

# 进入容器
docker exec -it my-panel-admin sh
docker exec -it my-panel-proxy1 sh
docker exec -it my-panel-proxy2 sh
docker exec -it my-panel-mysql mysql -uroot -p123456

# 查看容器资源占用
docker stats my-panel-admin my-panel-proxy1 my-panel-proxy2

# 查看容器详细信息
docker inspect my-panel-admin

# 查看容器内进程
docker top my-panel-admin

# 在运行中的容器内执行命令
docker exec my-panel-admin ls /my-panel/admin/config
docker exec my-panel-admin cat /my-panel/admin/config/application.yml


八、镜像操作
--------------------------------------------------------------------------------

# 查看本地镜像
docker images

# 删除镜像
docker rmi my-panel-docker-admin
docker rmi my-panel-docker-proxy1
docker rmi my-panel-docker-proxy2

# 清理未使用的镜像（释放磁盘）
docker image prune -a


九、数据管理
--------------------------------------------------------------------------------

# MySQL 数据存储位置（宿主机）
   ./data/mysql-8.4.10/data

# Redis 数据存储位置（宿主机）
   ./data/redis-8.6.4/data

# Admin 日志存储位置（宿主机）
   ./data/admin/logs

# Admin 上传文件存储位置（宿主机）
   ./data/admin/uploadPath

# Proxy1 日志存储位置（宿主机）
   ./data/proxy/logs

# Proxy2 日志存储位置（宿主机）
   ./data/proxy2/logs

# 备份数据库
docker exec my-panel-mysql mysqldump -uroot -p123456 --single-transaction --routines --triggers my-panel > my-panel_backup.sql

# 恢复数据库
docker exec -i my-panel-mysql mysql -uroot -p123456 my-panel < my-panel_backup.sql

# 清除所有数据重新初始化
docker compose down -v
rm -rf data/
docker compose up -d --build


十、配置修改与重启
--------------------------------------------------------------------------------

1. 修改管理后台配置
   vi config/admin/application.yml
   # 修改后重启 admin 生效
   ./my-panel.sh restart admin

2. 修改代理服务配置
   vi config/proxy/application.properties
   ./my-panel.sh restart proxy1 proxy2

3. 修改 MySQL 配置
   vi mysql/my.cnf
   ./my-panel.sh restart mysql

4. 修改端口映射
   vi docker-compose.yml
   # 修改 ports 部分后需要重建容器
   ./my-panel.sh up

5. 修改数据库连接 / Redis 连接
   vi docker-compose.yml
   # 修改 COMMON_DATASOURCE_URL / COMMON_REDIS_HOST 等环境变量
   ./my-panel.sh up

6. 修改 Proxy 注册地址
   docker-compose.yml 中 proxy1/proxy2 的 PROXY_REGISTRY_ADDRESS 和 PROXY_REGISTRY_PORT
   默认使用 HOST_IP 环境变量（由 my-panel.sh 自动检测物理机真实IP）
   也可手动设置: export HOST_IP=192.168.1.100


十一、健康检查
--------------------------------------------------------------------------------

# Admin 健康检查
curl http://localhost:8888/actuator/health

# Proxy1 健康检查
curl http://localhost:9876/api/health

# Proxy2 健康检查
curl http://localhost:9877/api/health

# nginx 前端控制台健康检查
curl -I http://localhost:9999/

# MySQL 连接检查
docker exec my-panel-mysql mysqladmin -uroot -p123456 ping

# Redis 连接检查
docker exec my-panel-redis redis-cli -p 7379 ping

# 一键检查所有服务健康状态
./my-panel.sh health


十二、端口说明
--------------------------------------------------------------------------------
  端口    服务          说明
  ----    ----          ----
  5306    MySQL         数据库
  7379    Redis         缓存
  8888    admin         管理后台 REST API（/api、/actuator）
  9876    proxy1        注册中心 & 代理服务
  9877    proxy2        注册中心 & 代理服务（双实例高可用）
  9999    nginx         前端控制台 Web UI + /api 反向代理到 admin 容器

  说明：日常使用浏览器访问 http://localhost:9999 即可打开前端控制台，
        nginx 会将 /api 请求反向代理到 admin 容器（8888），
        /admin 请求反向代理到 Spring Boot Admin 监控台。
        如需直接调试 REST API，可仍访问 http://localhost:8888。

        Proxy 采用双实例部署(proxy1、proxy2)，Admin 通过 PROXY_URLS
        环境变量配置两个 Proxy 地址，实现负载均衡和高可用。


十三、默认账号
--------------------------------------------------------------------------------
  admin / admin123
  guest / guest123
  cq    / cq123456


十四、常见问题排查
--------------------------------------------------------------------------------

Q1: 端口被占用（address already in use）
    解决: 修改 docker-compose.yml 中对应服务的 ports 映射
    例如将 "8888:8888" 改为 "18888:8888"

Q2: 容器启动后立即退出
    排查: docker compose logs <服务名>
    常见原因: 数据库连接失败、配置文件格式错误

Q3: MySQL 无法连接
    排查: docker exec my-panel-mysql mysql -uroot -p123456 -e "SELECT 1"
    原因1: MySQL 还在初始化中，等待 30 秒后重试
    原因2: docker-compose.yml 中 COMMON_DATASOURCE_URL 的 host 应为 mysql（容器名）

Q4: Redis 无法连接
    排查: docker exec my-panel-redis redis-cli -p 7379 ping
    原因: docker-compose.yml 中 COMMON_REDIS_HOST 应为 redis（容器名）

Q5: 修改配置不生效
    原因: 配置文件通过 volume 只读挂载，修改后需要重启容器
    解决: ./my-panel.sh restart admin

Q6: 磁盘空间不足
    解决: docker system prune -a  （清理未使用的镜像和容器）

Q7: Proxy2 健康检查失败
    原因: Proxy2 容器内端口为 9876，健康检查需使用容器内端口
    解决: 确保 docker-compose.yml 中 proxy2 的 healthcheck 使用 localhost:9876

Q8: Proxy 注册地址不正确
    原因: 多网卡环境下自动检测的 IP 可能不是期望的物理网卡 IP
    解决: 手动设置环境变量 export HOST_IP=192.168.x.x 后执行 ./my-panel.sh up


十五、卸载
--------------------------------------------------------------------------------

# 停止并删除所有容器
./my-panel.sh down

# 删除镜像
docker rmi my-panel-docker-admin my-panel-docker-proxy1 my-panel-docker-proxy2

# 删除数据（慎用）
rm -rf data/

# 删除部署目录
cd / && rm -rf /opt/my-panel


================================================================================
  文档版本: 1.1.0
  最后更新: 2026-07-10
================================================================================