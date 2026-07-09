================================================================================
                    My-Panel Docker 部署使用教程
================================================================================

一、项目简介
--------------------------------------------------------------------------------
My-Panel 是一个现代化的管理面板,基于 React 19 + Spring Boot 4 构建,
参考若依架构,支持双模式运行(Standalone/Cluster),并包含分布式 Agent 系统。

服务组件：
  1. MySQL 8.0.26      - 数据库（端口 33306）
  2. Redis 7           - 缓存（端口 6390）
  3. my-panel-admin    - 管理后台 REST API（端口 8888）
  4. my-panel-proxy    - 注册中心 & 配置中心代理服务（端口 9876）
  5. nginx 1.27        - 前端控制台 + API 反向代理（端口 9999）


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
   ├── my-panel-ui/
   │   └── dist/                # 前端构建产物（nginx 静态资源）
   │       ├── index.html
   │       └── assets/
   ├── mysql/
   │   ├── my.cnf               # MySQL 配置
   │   └── init/
   │       └── init-mysql.sql   # MySQL 初始化脚本（root 远程授权）
   ├── nginx/
   │   └── default.conf         # nginx 站点配置（前端 + API 反向代理）
   └── README.txt               # 本文档

3. 修改配置（按需）
   - 数据库连接: 修改 docker-compose.yml 中 COMMON_DATASOURCE_URL
   - Redis 连接: 修改 docker-compose.yml 中 COMMON_REDIS_HOST/PORT
   - Proxy 地址: 修改 docker-compose.yml 中 PROXY_URL
   - 端口冲突:   修改 docker-compose.yml 中 ports 映射

4. 构建镜像并启动
   docker compose up -d --build

5. 首次启动需要等待 30-60 秒（MySQL 初始化 + 服务启动）


四、Docker Compose 常用命令
--------------------------------------------------------------------------------
以下命令均在部署根目录（解压目录）执行。

# 启动所有服务（后台运行）
docker compose up -d --build

# 启动单个服务
docker compose up -d admin
docker compose up -d proxy
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


五、日志查看
--------------------------------------------------------------------------------

# 查看所有服务日志
docker compose logs

# 查看指定服务日志
docker compose logs admin
docker compose logs proxy
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


六、容器操作
--------------------------------------------------------------------------------

# 进入容器
docker exec -it my-panel-admin sh
docker exec -it my-panel-proxy sh
docker exec -it my-panel-mysql mysql -uroot -p123456

# 查看容器资源占用
docker stats my-panel-admin my-panel-proxy

# 查看容器详细信息
docker inspect my-panel-admin

# 查看容器内进程
docker top my-panel-admin

# 在运行中的容器内执行命令
docker exec my-panel-admin ls /my-panel/admin/config
docker exec my-panel-admin cat /my-panel/admin/config/application.yml


七、镜像操作
--------------------------------------------------------------------------------

# 查看本地镜像
docker images

# 删除镜像
docker rmi my-panel-admin
docker rmi my-panel-proxy

# 清理未使用的镜像（释放磁盘）
docker image prune -a


八、数据管理
--------------------------------------------------------------------------------

# MySQL 数据存储位置（宿主机）
   ./data/mysql-8.0.26/data

# Redis 数据存储位置（宿主机）
   ./data/redis-7/data

# Admin 日志存储位置（宿主机）
   ./data/admin/logs

# Admin 上传文件存储位置（宿主机）
   ./data/admin/uploadPath

# Proxy 日志存储位置（宿主机）
   ./data/proxy/logs

# 备份数据库
docker exec my-panel-mysql mysqldump -uroot -p123456 my-panel > my-panel_backup.sql

# 恢复数据库
docker exec -i my-panel-mysql mysql -uroot -p123456 my-panel < my-panel_backup.sql

# 清除所有数据重新初始化
docker compose down -v
rm -rf data/
docker compose up -d --build


九、配置修改与重启
--------------------------------------------------------------------------------

1. 修改管理后台配置
   vi config/admin/application.yml
   # 修改后重启 admin 生效
   docker compose restart admin

2. 修改代理服务配置
   vi config/proxy/application.properties
   docker compose restart proxy

3. 修改 MySQL 配置
   vi mysql/my.cnf
   docker compose restart mysql

4. 修改端口映射
   vi docker-compose.yml
   # 修改 ports 部分后需要重建容器
   docker compose up -d


十、健康检查
--------------------------------------------------------------------------------

# Admin 健康检查
curl http://localhost:8888/actuator/health

# Proxy 健康检查
curl http://localhost:9876/health

# nginx 前端控制台健康检查
curl -I http://localhost:9999/

# MySQL 连接检查
docker exec my-panel-mysql mysqladmin -uroot -p123456 ping

# Redis 连接检查
docker exec my-panel-redis redis-cli ping


十一、端口说明
--------------------------------------------------------------------------------
  端口    服务          说明
  ----    ----          ----
  33306   MySQL         数据库（映射为 33306 避免冲突）
  6390    Redis         缓存（映射为 6390 避免冲突）
  8888    admin         管理后台 REST API（/api、/actuator、/druid）
  9876    proxy         注册中心 & 配置中心代理服务
  9999    nginx         前端控制台 Web UI + /api 反向代理到 admin 容器

  说明：日常使用浏览器访问 http://localhost:9999 即可打开前端控制台，
        nginx 会将 /api 请求反向代理到 admin 容器（8888），
        /admin 请求反向代理到 Spring Boot Admin 监控台。
        如需直接调试 REST API，可仍访问 http://localhost:8888。


十二、默认账号
--------------------------------------------------------------------------------
  admin / admin123
  guest / guest123
  cq    / cq123456


十三、常见问题排查
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
    排查: docker exec my-panel-redis redis-cli ping
    原因: docker-compose.yml 中 COMMON_REDIS_HOST 应为 redis（容器名）

Q5: 修改配置不生效
    原因: 配置文件通过 volume 只读挂载，修改后需要重启容器
    解决: docker compose restart admin

Q6: 磁盘空间不足
    解决: docker system prune -a  （清理未使用的镜像和容器）


十四、卸载
--------------------------------------------------------------------------------

# 停止并删除所有容器
docker compose down

# 删除镜像
docker rmi my-panel-admin my-panel-proxy

# 删除数据（慎用）
rm -rf data/

# 删除部署目录
cd / && rm -rf /opt/my-panel


================================================================================
  文档版本: 1.0.0
  最后更新: 2026-07-08
================================================================================
