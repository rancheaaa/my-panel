#!/bin/bash

# =============================================
#  Redis 单机版一键管理脚本 (CentOS 9 / RHEL 9)
#  功能: 安装 / 启动 / 停止 / 重启 / 状态 / 端口管理 / 连接信息
# chmod +x install-redis.sh
#
# # 安装并初始化
# sudo ./install-redis.sh install
#
# # 自定义密码安装
# sudo REDIS_PASSWORD=YourPass123 ./install-redis.sh install
#
# # 其他操作
# sudo ./install-redis.sh start
# sudo ./install-redis.sh stop
# sudo ./install-redis.sh restart
# sudo ./install-redis.sh status
# sudo ./install-redis.sh conn
# sudo ./install-redis.sh setport 6380
# sudo ./install-redis.sh setpassword NewPass123
# sudo ./install-redis.sh enable
# sudo ./install-redis.sh disable
# sudo ./install-redis.sh uninstall
# =============================================

REDIS_PASSWORD="${REDIS_PASSWORD:-MyPanel@2026}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_CONF_DIR="/etc/redis"
REDIS_CONF_FILE="${REDIS_CONF_DIR}/redis.conf"
REDIS_DATA_DIR="/var/lib/redis"
REDIS_LOG_DIR="/var/log/redis"
REDIS_LOG_FILE="${REDIS_LOG_DIR}/redis.log"
REDIS_SERVICE="redis"
REDIS_PID_FILE="/var/run/redis/redis.pid"
REDIS_CUSTOM_CONF="${REDIS_CONF_DIR}/my-panel.conf"

# ─── 颜色定义 ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ─── 权限检查 ───
check_root() {
    if [ "$EUID" -ne 0 ]; then
        error "请使用 root 用户执行此脚本"
        exit 1
    fi
}

# ─── 检测 Redis 是否已安装 ───
is_redis_installed() {
    rpm -q redis &>/dev/null
}

# ─── 检测 Redis 是否运行中 ───
is_redis_running() {
    systemctl is-active --quiet "$REDIS_SERVICE" 2>/dev/null
}

# ─── 获取当前 Redis 端口 ───
get_redis_port() {
    local port
    # 优先从自定义配置读取
    if [ -f "$REDIS_CUSTOM_CONF" ]; then
        port=$(grep -P '^\s*port\s+' "$REDIS_CUSTOM_CONF" | head -1 | awk '{print $2}' | tr -d ' ')
    fi
    # 其次从主配置读取
    if [ -z "$port" ] && [ -f "$REDIS_CONF_FILE" ]; then
        port=$(grep -P '^\s*port\s+' "$REDIS_CONF_FILE" | head -1 | awk '{print $2}' | tr -d ' ')
    fi
    # 从运行时获取
    if [ -z "$port" ] && is_redis_running; then
        port=$(ss -tlnp 2>/dev/null | grep redis-server | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
    fi
    echo "${port:-6379}"
}

# ─── 获取当前 Redis 密码 ───
get_redis_password() {
    local pass
    if [ -f "$REDIS_CUSTOM_CONF" ]; then
        pass=$(grep -P '^\s*requirepass\s+' "$REDIS_CUSTOM_CONF" | head -1 | awk '{print $2}' | tr -d ' "')
    fi
    if [ -z "$pass" ] && [ -f "$REDIS_CONF_FILE" ]; then
        pass=$(grep -P '^\s*requirepass\s+' "$REDIS_CONF_FILE" | head -1 | awk '{print $2}' | tr -d ' "')
    fi
    echo "$pass"
}

# ─── 执行 redis-cli 命令 ───
redis_cli_exec() {
    local port
    port=$(get_redis_port)
    local pass
    pass=$(get_redis_password)
    if [ -n "$pass" ]; then
        redis-cli -h 127.0.0.1 -p "$port" -a "$pass" --no-auth-warning "$@" 2>/dev/null
    else
        redis-cli -h 127.0.0.1 -p "$port" "$@" 2>/dev/null
    fi
}

# ─── 等待 Redis 就绪 ───
wait_for_redis() {
    local max_wait=30
    local elapsed=0
    info "等待 Redis 启动就绪..."
    while [ $elapsed -lt $max_wait ]; do
        if redis_cli_exec PING 2>/dev/null | grep -q PONG; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

# ─── 安装 Redis ───
install_redis() {
    check_root

    if is_redis_installed; then
        info "Redis 已安装: $(rpm -q redis)"
        return 0
    fi

    info "开始安装 Redis..."

    # 确保 dnf 缓存是最新的
    dnf makecache --refresh >/dev/null 2>&1 || true

    # 安装 Redis
    info "安装 redis..."
    if ! dnf install -y redis; then
        error "Redis 安装失败，请检查 dnf 源是否正常"
        exit 1
    fi

    if ! is_redis_installed; then
        error "Redis 安装验证失败"
        exit 1
    fi

    info "Redis 安装成功: $(rpm -q redis)"

    # 创建必要目录
    mkdir -p "$REDIS_DATA_DIR"
    mkdir -p "$REDIS_LOG_DIR"
    mkdir -p "$(dirname "$REDIS_PID_FILE")"
    chown -R redis:redis "$REDIS_DATA_DIR"
    chown -R redis:redis "$REDIS_LOG_DIR"
    chown -R redis:redis "$(dirname "$REDIS_PID_FILE")"

    # 配置 Redis
    configure_redis

    # 启动并设置开机自启
    start_redis
    enable_redis

    # 验证连接
    if redis_cli_exec PING 2>/dev/null | grep -q PONG; then
        info "Redis 连接验证成功"
    else
        warn "Redis 连接验证失败，请检查配置"
    fi

    info "Redis 安装配置完成！"
    show_status
}

# ─── 配置 Redis ───
configure_redis() {
    info "配置 Redis..."

    # 写入自定义配置（include 方式，不修改主配置文件）
    cat > "$REDIS_CUSTOM_CONF" <<EOF
# My-Panel 自定义配置
# 绑定地址
bind 0.0.0.0

# 端口
port ${REDIS_PORT}

# 密码认证
requirepass ${REDIS_PASSWORD}

# 后台运行 (systemd 管理时设为 no)
daemonize no

# PID 文件
pidfile ${REDIS_PID_FILE}

# 日志
logfile ${REDIS_LOG_FILE}
loglevel notice

# 数据目录
dir ${REDIS_DATA_DIR}

# 持久化 - RDB
save 900 1
save 300 10
save 60 10000
rdbcompression yes
rdbchecksum yes
dbfilename dump.rdb

# 持久化 - AOF
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# 内存
maxmemory 256mb
maxmemory-policy allkeys-lru

# 客户端
maxclients 10000
timeout 300
tcp-keepalive 60

# 性能
hz 10
dynamic-hz yes

# 慢日志
slowlog-log-slower-than 10000
slowlog-max-len 128
EOF

    chown redis:redis "$REDIS_CUSTOM_CONF"

    # 在主配置文件末尾添加 include（如果尚未添加）
    if ! grep -q "my-panel.conf" "$REDIS_CONF_FILE" 2>/dev/null; then
        echo "" >> "$REDIS_CONF_FILE"
        echo "# My-Panel custom config" >> "$REDIS_CONF_FILE"
        echo "include ${REDIS_CUSTOM_CONF}" >> "$REDIS_CONF_FILE"
    fi

    info "Redis 配置文件已写入: ${REDIS_CUSTOM_CONF}"
    info "密码: ${REDIS_PASSWORD}"
    info "端口: ${REDIS_PORT}"
}

# ─── 启动 Redis ───
start_redis() {
    check_root

    if is_redis_running; then
        info "Redis 已在运行中"
        return 0
    fi

    info "启动 Redis..."
    systemctl start "$REDIS_SERVICE"

    if wait_for_redis; then
        info "Redis 启动成功"
    else
        error "Redis 启动超时，请检查日志: ${REDIS_LOG_FILE}"
        journalctl -u "$REDIS_SERVICE" --no-pager -n 20
        exit 1
    fi
}

# ─── 停止 Redis ───
stop_redis() {
    check_root

    if ! is_redis_running; then
        info "Redis 未在运行"
        return 0
    fi

    info "停止 Redis..."

    # 优雅关闭：先执行 SHUTDOWN NOSAVE
    redis_cli_exec SHUTDOWN NOSAVE 2>/dev/null || true

    local max_wait=15
    local elapsed=0
    while [ $elapsed -lt $max_wait ]; do
        if ! is_redis_running; then
            info "Redis 已停止"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    # 优雅关闭失败，使用 systemctl
    warn "优雅关闭超时，使用 systemctl stop..."
    systemctl stop "$REDIS_SERVICE"

    sleep 2
    if ! is_redis_running; then
        info "Redis 已停止"
    else
        error "Redis 无法停止，尝试强制终止..."
        killall -9 redis-server 2>/dev/null || true
        sleep 1
        if ! is_redis_running; then
            warn "Redis 已强制终止"
        else
            error "Redis 无法停止，请手动排查"
            exit 1
        fi
    fi
}

# ─── 重启 Redis ───
restart_redis() {
    check_root

    info "重启 Redis..."
    if ! systemctl restart "$REDIS_SERVICE" 2>&1; then
        error "Redis 重启失败，查看最近日志:"
        if [ -f "$REDIS_LOG_FILE" ]; then
            tail -30 "$REDIS_LOG_FILE"
        else
            journalctl -u "$REDIS_SERVICE" --no-pager -n 20
        fi
        return 1
    fi

    if wait_for_redis; then
        info "Redis 重启成功"
    else
        error "Redis 重启超时，查看最近日志:"
        if [ -f "$REDIS_LOG_FILE" ]; then
            tail -30 "$REDIS_LOG_FILE"
        else
            journalctl -u "$REDIS_SERVICE" --no-pager -n 20
        fi
        return 1
    fi
}

# ─── 设置开机自启 ───
enable_redis() {
    check_root

    if systemctl is-enabled --quiet "$REDIS_SERVICE" 2>/dev/null; then
        info "Redis 开机自启已启用"
        return 0
    fi

    info "设置 Redis 开机自启..."
    systemctl enable "$REDIS_SERVICE"
    info "Redis 开机自启已启用"
}

# ─── 取消开机自启 ───
disable_redis() {
    check_root

    if ! systemctl is-enabled --quiet "$REDIS_SERVICE" 2>/dev/null; then
        info "Redis 开机自启未启用"
        return 0
    fi

    info "取消 Redis 开机自启..."
    systemctl disable "$REDIS_SERVICE"
    info "Redis 开机自启已取消"
}

# ─── 显示状态 ───
show_status() {
    echo ""
    echo "============================================="
    echo "  Redis 状态报告"
    echo "============================================="

    # 安装状态
    if is_redis_installed; then
        info "安装状态: 已安装 ($(rpm -q redis))"
    else
        error "安装状态: 未安装"
        echo "  请先执行: $0 install"
        echo "============================================="
        return
    fi

    # 运行状态
    if is_redis_running; then
        info "运行状态: 运行中 (PID: $(pgrep -x redis-server | head -1))"
    else
        warn "运行状态: 未运行"
    fi

    # 自启状态
    if systemctl is-enabled --quiet "$REDIS_SERVICE" 2>/dev/null; then
        info "开机自启: 已启用"
    else
        warn "开机自启: 未启用"
    fi

    # 端口
    local port
    port=$(get_redis_port)
    local listen_port
    listen_port=$(ss -tlnp 2>/dev/null | grep redis-server | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
    if [ -n "$listen_port" ]; then
        info "监听端口: ${listen_port}"
    else
        info "配置端口: ${port} (未监听)"
    fi

    # 密码
    local pass
    pass=$(get_redis_password)
    if [ -n "$pass" ]; then
        info "密码认证: 已启用"
    else
        warn "密码认证: 未启用 (建议设置密码)"
    fi

    # 运行时信息
    if is_redis_running; then
        local version
        version=$(redis_cli_exec INFO server 2>/dev/null | grep '^redis_version' | awk -F: '{print $2}' | tr -d '\r')
        [ -n "$version" ] && info "版本: $version"

        local uptime
        uptime=$(redis_cli_exec INFO server 2>/dev/null | grep '^uptime_in_seconds' | awk -F: '{print $2}' | tr -d '\r')
        if [ -n "$uptime" ]; then
            local hours=$((uptime / 3600))
            local mins=$(( (uptime % 3600) / 60 ))
            info "运行时长: ${hours}小时${mins}分钟"
        fi

        local memory
        memory=$(redis_cli_exec INFO memory 2>/dev/null | grep '^used_memory_human' | awk -F: '{print $2}' | tr -d '\r')
        [ -n "$memory" ] && info "内存使用: $memory"

        local maxmem
        maxmem=$(redis_cli_exec INFO memory 2>/dev/null | grep '^maxmemory_human' | awk -F: '{print $2}' | tr -d '\r')
        [ -n "$maxmem" ] && info "内存上限: $maxmem"

        local keyspace
        keyspace=$(redis_cli_exec INFO keyspace 2>/dev/null | grep '^db0' | tr -d '\r')
        [ -n "$keyspace" ] && info "键空间: $keyspace"

        local connected_clients
        connected_clients=$(redis_cli_exec INFO clients 2>/dev/null | grep '^connected_clients' | awk -F: '{print $2}' | tr -d '\r')
        [ -n "$connected_clients" ] && info "连接数: $connected_clients"
    fi

    # 数据目录
    if [ -d "$REDIS_DATA_DIR" ]; then
        local db_size
        db_size=$(du -sh "$REDIS_DATA_DIR" 2>/dev/null | awk '{print $1}')
        info "数据目录: $REDIS_DATA_DIR ($db_size)"
    fi

    echo "============================================="
}

# ─── 显示连接信息 ───
show_conn() {
    echo ""
    echo "============================================="
    echo "  Redis 连接信息"
    echo "============================================="

    if ! is_redis_installed; then
        error "Redis 未安装"
        echo "  请先执行: $0 install"
        echo "============================================="
        return
    fi

    if ! is_redis_running; then
        error "Redis 未运行"
        echo "  请先执行: $0 start"
        echo "============================================="
        return
    fi

    local port
    port=$(get_redis_port)
    local pass
    pass=$(get_redis_password)
    local local_ip
    local_ip=$(hostname -I 2>/dev/null | awk '{print $1}')

    echo ""
    info "监听端口: ${port}"
    info "密码:     ${pass:-无}"
    info "本机 IP:  ${local_ip:-无法获取}"
    echo ""
    echo "  连接方式:"
    echo ""
    echo "  本机连接:"
    if [ -n "$pass" ]; then
        echo "    redis-cli -h 127.0.0.1 -p ${port} -a '${pass}'"
    else
        echo "    redis-cli -h 127.0.0.1 -p ${port}"
    fi
    echo ""
    echo "  远程连接:"
    if [ -n "$pass" ]; then
        echo "    redis-cli -h ${local_ip:-<服务器IP>} -p ${port} -a '${pass}'"
    else
        echo "    redis-cli -h ${local_ip:-<服务器IP>} -p ${port}"
    fi
    echo ""
    echo "  Spring Boot 配置:"
    echo "    spring.data.redis.host=${local_ip:-<服务器IP>}"
    echo "    spring.data.redis.port=${port}"
    if [ -n "$pass" ]; then
        echo "    spring.data.redis.password=${pass}"
    fi
    echo ""
    echo "============================================="
}

# ─── 修改监听端口 ───
setport_redis() {
    check_root

    local new_port="${2:-}"

    if [ -z "$new_port" ]; then
        error "请指定端口号，例如: $0 setport 6380"
        exit 1
    fi

    # 端口合法性校验
    if ! [[ "$new_port" =~ ^[0-9]+$ ]] || [ "$new_port" -lt 1 ] || [ "$new_port" -gt 65535 ]; then
        error "无效端口号: $new_port (有效范围: 1-65535)"
        exit 1
    fi

    if ! is_redis_installed; then
        error "Redis 未安装，请先执行: $0 install"
        exit 1
    fi

    local current_port
    current_port=$(get_redis_port)
    if [ "$current_port" = "$new_port" ]; then
        info "Redis 已在端口 $new_port，无需修改"
        return 0
    fi

    # 检查端口是否被占用
    if ss -tlnp 2>/dev/null | grep -q ":${new_port} "; then
        local occupier
        occupier=$(ss -tlnp 2>/dev/null | grep ":${new_port} " | awk '{print $6}')
        error "端口 $new_port 已被占用: $occupier"
        exit 1
    fi

    # 处理防火墙
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        info "防火墙: 放开新端口 ${new_port}/tcp..."
        firewall-cmd --permanent --add-port="${new_port}/tcp" 2>/dev/null || true
        info "防火墙: 移除旧端口 ${current_port}/tcp..."
        firewall-cmd --permanent --remove-port="${current_port}/tcp" 2>/dev/null || true
        firewall-cmd --reload 2>/dev/null || true
        info "防火墙: 规则已更新并重载"
    fi

    # 备份配置
    local conf_bak="${REDIS_CUSTOM_CONF}.bak"
    [ -f "$REDIS_CUSTOM_CONF" ] && cp -f "$REDIS_CUSTOM_CONF" "$conf_bak"

    # 修改端口
    if [ -f "$REDIS_CUSTOM_CONF" ]; then
        sed -i "s/^\s*port\s\+.*/port ${new_port}/" "$REDIS_CUSTOM_CONF"
    else
        # 没有自定义配置文件，创建一个
        cat > "$REDIS_CUSTOM_CONF" <<EOF
port ${new_port}
EOF
        chown redis:redis "$REDIS_CUSTOM_CONF"
        # 确保 include 存在
        if ! grep -q "my-panel.conf" "$REDIS_CONF_FILE" 2>/dev/null; then
            echo "include ${REDIS_CUSTOM_CONF}" >> "$REDIS_CONF_FILE"
        fi
    fi

    info "端口配置已修改: ${current_port} -> ${new_port}"

    # 重启 Redis 使配置生效
    if is_redis_running; then
        if restart_redis; then
            sleep 1
            local verify_port
            verify_port=$(ss -tlnp 2>/dev/null | grep redis-server | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
            if [ "$verify_port" = "$new_port" ]; then
                info "端口修改成功，当前监听端口: $new_port"
            else
                warn "端口验证结果: ${verify_port:-未检测到}，请检查 Redis 是否正常启动"
            fi
            rm -f "$conf_bak"
        else
            # 重启失败，回滚
            error "Redis 重启失败，回滚端口配置..."
            if [ -f "$conf_bak" ]; then
                mv -f "$conf_bak" "$REDIS_CUSTOM_CONF"
            fi
            warn "已回滚端口配置，尝试恢复 Redis..."
            systemctl start "$REDIS_SERVICE" 2>/dev/null || true
            exit 1
        fi
    else
        info "Redis 未运行，下次启动时将使用新端口 $new_port"
    fi
}

# ─── 修改密码 ───
setpassword_redis() {
    check_root

    local new_password="${2:-}"

    if [ -z "$new_password" ]; then
        error "请指定密码，例如: $0 setpassword YourNewPass123"
        exit 1
    fi

    if ! is_redis_installed; then
        error "Redis 未安装，请先执行: $0 install"
        exit 1
    fi

    # 备份配置
    local conf_bak="${REDIS_CUSTOM_CONF}.bak"
    [ -f "$REDIS_CUSTOM_CONF" ] && cp -f "$REDIS_CUSTOM_CONF" "$conf_bak"

    # 修改密码
    if [ -f "$REDIS_CUSTOM_CONF" ]; then
        if grep -q '^\s*requirepass' "$REDIS_CUSTOM_CONF"; then
            sed -i "s/^\s*requirepass\s\+.*/requirepass ${new_password}/" "$REDIS_CUSTOM_CONF"
        else
            echo "requirepass ${new_password}" >> "$REDIS_CUSTOM_CONF"
        fi
    else
        cat > "$REDIS_CUSTOM_CONF" <<EOF
requirepass ${new_password}
EOF
        chown redis:redis "$REDIS_CUSTOM_CONF"
        if ! grep -q "my-panel.conf" "$REDIS_CONF_FILE" 2>/dev/null; then
            echo "include ${REDIS_CUSTOM_CONF}" >> "$REDIS_CONF_FILE"
        fi
    fi

    info "密码配置已修改"

    # 运行时也修改（无需重启即可生效）
    if is_redis_running; then
        local old_pass
        old_pass=$(get_redis_password)
        # 用旧密码连接修改运行时密码
        if [ -n "$old_pass" ]; then
            redis-cli -h 127.0.0.1 -p "$(get_redis_port)" -a "$old_pass" --no-auth-warning CONFIG SET requirepass "$new_password" 2>/dev/null
        else
            redis-cli -h 127.0.0.1 -p "$(get_redis_port)" CONFIG SET requirepass "$new_password" 2>/dev/null
        fi
        info "运行时密码已同步修改"
    fi

    REDIS_PASSWORD="$new_password"
    info "Redis 密码已设置为: ${new_password}"
    rm -f "$conf_bak"
}

# ─── 卸载 Redis ───
uninstall_redis() {
    check_root

    warn "即将卸载 Redis，所有数据将丢失！"
    read -p "确认卸载？输入 YES 确认: " confirm
    if [ "$confirm" != "YES" ]; then
        info "已取消卸载"
        return 0
    fi

    stop_redis
    disable_redis

    info "卸载 Redis 包..."
    dnf remove -y redis 2>/dev/null || true

    info "清理数据目录..."
    rm -rf "$REDIS_DATA_DIR"
    rm -rf "$REDIS_LOG_DIR"
    rm -f "$REDIS_CUSTOM_CONF"

    info "Redis 已完全卸载"
}

# ─── 使用帮助 ───
show_help() {
    cat <<EOF
Redis 单机版一键管理脚本

用法: $0 <命令> [参数]

命令:
  install          安装 Redis 并完成初始化配置
  start            启动 Redis
  stop             停止 Redis
  restart          重启 Redis
  status           查看 Redis 状态
  conn             显示 Redis 连接信息（IP、端口、密码、连接串）
  setport <端口>   修改 Redis 监听端口
  setpassword <密码>  修改 Redis 密码
  enable           设置开机自启
  disable          取消开机自启
  uninstall        完全卸载 Redis（含数据）

环境变量:
  REDIS_PASSWORD   Redis 密码 (默认: MyPanel@2026)
  REDIS_PORT       Redis 端口 (默认: 6379)

示例:
  $0 install                              # 安装并初始化
  REDIS_PASSWORD=NewPass123 $0 install    # 指定密码安装
  REDIS_PORT=6380 $0 install              # 指定端口安装
  $0 status                               # 查看状态
  $0 conn                                 # 查看连接信息
  $0 setport 6380                         # 修改监听端口为 6380
  $0 setpassword NewPass456               # 修改密码
  $0 restart                              # 重启
EOF
}

# ─── 主入口 ───
case "${1:-}" in
    install)
        install_redis
        ;;
    start)
        start_redis
        ;;
    stop)
        stop_redis
        ;;
    restart)
        restart_redis
        ;;
    status)
        show_status
        ;;
    conn)
        show_conn
        ;;
    setport)
        setport_redis "$@"
        ;;
    setpassword)
        setpassword_redis "$@"
        ;;
    enable)
        enable_redis
        ;;
    disable)
        disable_redis
        ;;
    uninstall)
        uninstall_redis
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        show_help
        exit 1
        ;;
esac
