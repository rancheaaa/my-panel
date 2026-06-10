#!/bin/bash

# =============================================
#  MySQL 8 一键管理脚本 (CentOS 9 / RHEL 9)
#  功能: 安装 / 启动 / 停止 / 重启 / 状态检测 / 连接信息
# chmod +x install-mysql8.sh
#
# # 安装并初始化
# sudo ./install-mysql8.sh install
#
# # 自定义 root 密码安装
# sudo MYSQL_ROOT_PASSWORD=YourPass123 ./install-mysql8.sh install
#
# # 其他操作
# sudo ./install-mysql8.sh start
# sudo ./install-mysql8.sh stop
# sudo ./install-mysql8.sh restart
# sudo ./install-mysql8.sh status
# sudo ./install-mysql8.sh conn
# sudo ./install-mysql8.sh setport 3307
# sudo ./install-mysql8.sh enable
# sudo ./install-mysql8.sh disable
# sudo ./install-mysql8.sh uninstall
# =============================================

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-MyPanel@2026}"
MYSQL_DATA_DIR="${MYSQL_DATA_DIR:-/var/lib/mysql}"
MYSQL_CONF_DIR="/etc/my.cnf.d"
MYSQL_LOG_DIR="/var/log/mysql"
MYSQL_SERVICE="mysqld"

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

# ─── 检测 MySQL 是否已安装 ───
is_mysql_installed() {
    if rpm -q mysql-server &>/dev/null; then
        return 0
    fi
    return 1
}

# ─── 检测 MySQL 是否运行中 ───
is_mysql_running() {
    systemctl is-active --quiet "$MYSQL_SERVICE" 2>/dev/null
}

# ─── 获取当前 MySQL 监听端口 ───
get_mysql_port() {
    local port
    # 优先从配置文件读取
    if [ -f "${MYSQL_CONF_DIR}/my-panel-port.cnf" ]; then
        port=$(grep -P '^\s*port\s*=' "${MYSQL_CONF_DIR}/my-panel-port.cnf" | head -1 | awk -F= '{print $2}' | tr -d ' ')
    fi
    # 其次从运行时获取
    if [ -z "$port" ] && is_mysql_running; then
        port=$(ss -tlnp 2>/dev/null | grep mysqld | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
    fi
    echo "${port:-3306}"
}

# ─── 等待 MySQL 就绪 ───
wait_for_mysql() {
    local max_wait=60
    local elapsed=0
    local port
    port=$(get_mysql_port)
    info "等待 MySQL 启动就绪 (端口: ${port})..."
    while [ $elapsed -lt $max_wait ]; do
        if mysqladmin ping -h 127.0.0.1 -P "$port" --silent 2>/dev/null; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}

# ─── 安装 MySQL ───
install_mysql() {
    check_root

    if is_mysql_installed; then
        info "MySQL 已安装: $(rpm -q mysql-server)"
        return 0
    fi

    info "开始安装 MySQL 8..."

    # 确保 dnf 缓存是最新的
    dnf makecache --refresh >/dev/null 2>&1 || true

    # 安装 MySQL Server 及相关组件
    info "安装 mysql-server..."
    if ! dnf install -y mysql-server; then
        error "MySQL 安装失败，请检查 dnf 源是否正常"
        exit 1
    fi

    # 验证安装
    if ! is_mysql_installed; then
        error "MySQL 安装验证失败"
        exit 1
    fi

    info "MySQL 安装成功: $(rpm -q mysql-server)"

    # 创建日志目录
    mkdir -p "$MYSQL_LOG_DIR"
    chown mysql:mysql "$MYSQL_LOG_DIR"

    # 配置 MySQL
    configure_mysql

    # 启动并设置开机自启
    start_mysql
    enable_mysql

    # 初始化安全设置
    secure_mysql

    info "MySQL 8 安装配置完成！"
    show_status
}

# ─── 配置 MySQL ───
configure_mysql() {
    info "配置 MySQL..."

    cat > "${MYSQL_CONF_DIR}/my-panel.cnf" <<EOF
[mysqld]
# 字符集
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# 连接
max_connections=500
max_connect_errors=1000
wait_timeout=28800
interactive_timeout=28800

# 缓冲
innodb_buffer_pool_size=256M
innodb_log_buffer_size=16M
innodb_log_file_size=64M

# 日志
log_error=${MYSQL_LOG_DIR}/error.log
slow_query_log=1
slow_query_log_file=${MYSQL_LOG_DIR}/slow.log
long_query_time=2

# 其他
default-storage-engine=InnoDB
lower_case_table_names=1
sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION

[client]
default-character-set=utf8mb4
EOF

    chown mysql:mysql "${MYSQL_CONF_DIR}/my-panel.cnf"
    info "MySQL 配置文件已写入: ${MYSQL_CONF_DIR}/my-panel.cnf"
}

# ─── 初始化安全设置 ───
secure_mysql() {
    info "配置 MySQL 安全设置..."

    # 获取临时密码（MySQL 8 首次启动会生成）
    local temp_password
    temp_password=$(grep 'temporary password' "${MYSQL_LOG_DIR}/error.log" 2>/dev/null | tail -1 | awk '{print $NF}')

    if [ -n "$temp_password" ]; then
        info "检测到临时密码，正在修改 root 密码..."

        # 先修改密码策略，再设置新密码
        mysql --connect-expired-password -u root -p"${temp_password}" 2>/dev/null <<SQL || true
ALTER USER 'root'@'localhost' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
SET GLOBAL validate_password.policy=LOW;
SET GLOBAL validate_password.length=6;
ALTER USER 'root'@'localhost' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
SQL
    else
        # 没有临时密码，直接尝试设置
        mysql -u root 2>/dev/null <<SQL || true
ALTER USER 'root'@'localhost' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
SQL

        # 如果上面失败，尝试无密码连接后设置
        mysql -u root --skip-password 2>/dev/null <<SQL || true
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASSWORD}';
SQL
    fi

    # 创建远程访问用户（可选）
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" 2>/dev/null <<SQL || true
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASSWORD}';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL

    info "root 密码已设置为: ${MYSQL_ROOT_PASSWORD}"
    warn "请妥善保管密码，建议尽快修改默认密码"
}

# ─── 启动 MySQL ───
start_mysql() {
    check_root

    if is_mysql_running; then
        info "MySQL 已在运行中"
        return 0
    fi

    info "启动 MySQL..."
    systemctl start "$MYSQL_SERVICE"

    if wait_for_mysql; then
        info "MySQL 启动成功"
    else
        error "MySQL 启动超时，请检查日志: ${MYSQL_LOG_DIR}/error.log"
        journalctl -u "$MYSQL_SERVICE" --no-pager -n 20
        exit 1
    fi
}

# ─── 停止 MySQL ───
stop_mysql() {
    check_root

    if ! is_mysql_running; then
        info "MySQL 未在运行"
        return 0
    fi

    info "停止 MySQL..."
    systemctl stop "$MYSQL_SERVICE"

    local max_wait=30
    local elapsed=0
    while [ $elapsed -lt $max_wait ]; do
        if ! is_mysql_running; then
            info "MySQL 已停止"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    error "MySQL 停止超时，尝试强制终止..."
    killall -9 mysqld 2>/dev/null || true
    sleep 2
    if ! is_mysql_running; then
        warn "MySQL 已强制终止"
    else
        error "MySQL 无法停止，请手动排查"
        exit 1
    fi
}

# ─── 重启 MySQL ───
restart_mysql() {
    check_root

    info "重启 MySQL..."
    if ! systemctl restart "$MYSQL_SERVICE" 2>&1; then
        error "MySQL 重启失败，查看最近日志:"
        # 优先读取 MySQL error log
        local error_log="${MYSQL_LOG_DIR}/error.log"
        if [ ! -f "$error_log" ]; then
            error_log=$(grep -r 'log-error' /etc/my.cnf /etc/my.cnf.d/ 2>/dev/null | grep -v '^#' | awk -F= '{print $2}' | tr -d ' ' | head -1)
        fi
        if [ -f "$error_log" ]; then
            tail -30 "$error_log"
        else
            journalctl -u "$MYSQL_SERVICE" --no-pager -n 20
        fi
        return 1
    fi

    if wait_for_mysql; then
        info "MySQL 重启成功"
    else
        error "MySQL 重启超时，查看最近日志:"
        local error_log="${MYSQL_LOG_DIR}/error.log"
        if [ ! -f "$error_log" ]; then
            error_log=$(grep -r 'log-error' /etc/my.cnf /etc/my.cnf.d/ 2>/dev/null | grep -v '^#' | awk -F= '{print $2}' | tr -d ' ' | head -1)
        fi
        if [ -f "$error_log" ]; then
            tail -30 "$error_log"
        else
            journalctl -u "$MYSQL_SERVICE" --no-pager -n 20
        fi
        return 1
    fi
}

# ─── 设置开机自启 ───
enable_mysql() {
    check_root

    if systemctl is-enabled --quiet "$MYSQL_SERVICE" 2>/dev/null; then
        info "MySQL 开机自启已启用"
        return 0
    fi

    info "设置 MySQL 开机自启..."
    systemctl enable "$MYSQL_SERVICE"
    info "MySQL 开机自启已启用"
}

# ─── 取消开机自启 ───
disable_mysql() {
    check_root

    if ! systemctl is-enabled --quiet "$MYSQL_SERVICE" 2>/dev/null; then
        info "MySQL 开机自启未启用"
        return 0
    fi

    info "取消 MySQL 开机自启..."
    systemctl disable "$MYSQL_SERVICE"
    info "MySQL 开机自启已取消"
}

# ─── 显示状态 ───
show_status() {
    echo ""
    echo "============================================="
    echo "  MySQL 8 状态报告"
    echo "============================================="

    # 安装状态
    if is_mysql_installed; then
        info "安装状态: 已安装 ($(rpm -q mysql-server))"
    else
        error "安装状态: 未安装"
        echo "  请先执行: $0 install"
        echo "============================================="
        return
    fi

    # 运行状态
    if is_mysql_running; then
        info "运行状态: 运行中 (PID: $(pgrep -x mysqld | head -1))"
    else
        warn "运行状态: 未运行"
    fi

    # 自启状态
    if systemctl is-enabled --quiet "$MYSQL_SERVICE" 2>/dev/null; then
        info "开机自启: 已启用"
    else
        warn "开机自启: 未启用"
    fi

    # 端口监听
    local port
    port=$(ss -tlnp 2>/dev/null | grep mysqld | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
    if [ -n "$port" ]; then
        info "监听端口: ${port:-3306}"
    else
        warn "监听端口: 未监听"
    fi

    # 数据目录
    if [ -d "$MYSQL_DATA_DIR" ]; then
        local db_size
        db_size=$(du -sh "$MYSQL_DATA_DIR" 2>/dev/null | awk '{print $1}')
        info "数据目录: $MYSQL_DATA_DIR ($db_size)"
    fi

    # 版本信息
    if is_mysql_running; then
        local version
        version=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SELECT VERSION();" -s -N 2>/dev/null || echo "无法获取")
        info "版本: $version"

        local uptime
        uptime=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW STATUS LIKE 'Uptime';" -s -N 2>/dev/null | awk '{print $2}')
        if [ -n "$uptime" ]; then
            local hours=$((uptime / 3600))
            local mins=$(( (uptime % 3600) / 60 ))
            info "运行时长: ${hours}小时${mins}分钟"
        fi

        local databases
        databases=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW DATABASES;" -s 2>/dev/null | wc -l)
        info "数据库数量: $databases"
    fi

    echo "============================================="
}

# ─── 显示连接信息 ───
show_conn() {
    echo ""
    echo "============================================="
    echo "  MySQL 连接信息"
    echo "============================================="

    if ! is_mysql_installed; then
        error "MySQL 未安装"
        echo "  请先执行: $0 install"
        echo "============================================="
        return
    fi

    if ! is_mysql_running; then
        error "MySQL 未运行"
        echo "  请先执行: $0 start"
        echo "============================================="
        return
    fi

    # 获取监听地址和端口
    local listen_info
    listen_info=$(ss -tlnp 2>/dev/null | grep mysqld | head -1 | awk '{print $4}')

    local bind_ip="0.0.0.0"
    local port="3306"

    if [ -n "$listen_info" ]; then
        # 解析 IP:PORT
        if echo "$listen_info" | grep -q ':'; then
            bind_ip=$(echo "$listen_info" | rev | cut -d: -f2- | rev)
            port=$(echo "$listen_info" | rev | cut -d: -f1 | rev)
        fi
    fi

    # 从 MySQL 获取更精确的信息
    local mysql_host mysql_port
    mysql_info=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW VARIABLES LIKE 'hostname'; SHOW VARIABLES LIKE 'port'; SHOW VARIABLES LIKE 'bind_address';" -s -N 2>/dev/null)

    if [ -n "$mysql_info" ]; then
        mysql_host=$(echo "$mysql_info" | grep -A1 'hostname' | tail -1 | awk '{print $2}')
        mysql_port=$(echo "$mysql_info" | grep '^port' | awk '{print $2}')
        local bind_address
        bind_address=$(echo "$mysql_info" | grep '^bind_address' | awk '{print $2}')

        [ -n "$mysql_host" ] && bind_ip="$mysql_host"
        [ -n "$mysql_port" ] && port="$mysql_port"
        [ -n "$bind_address" ] && bind_ip="$bind_address"
    fi

    # 获取本机实际 IP
    local local_ip
    local_ip=$(hostname -I 2>/dev/null | awk '{print $1}')

    # 显示信息
    echo ""
    info "监听地址: ${bind_ip}"
    info "监听端口: ${port}"
    info "本机 IP:  ${local_ip:-无法获取}"
    echo ""
    echo "  连接方式:"
    echo ""
    echo "  本机连接:"
    echo "    mysql -u root -p'${MYSQL_ROOT_PASSWORD}' -h 127.0.0.1 -P ${port}"
    echo ""
    echo "  远程连接:"
    echo "    mysql -u root -p'${MYSQL_ROOT_PASSWORD}' -h ${local_ip:-<服务器IP>} -P ${port}"
    echo ""
    echo "  JDBC URL:"
    echo "    jdbc:mysql://${local_ip:-<服务器IP>}:${port}/your_database?useSSL=false&characterEncoding=utf8mb4"
    echo ""
    echo "============================================="
}

# ─── 修改监听端口 ───
setport_mysql() {
    check_root

    local new_port="${2:-}"

    if [ -z "$new_port" ]; then
        error "请指定端口号，例如: $0 setport 3307"
        exit 1
    fi

    # 端口合法性校验
    if ! [[ "$new_port" =~ ^[0-9]+$ ]] || [ "$new_port" -lt 1 ] || [ "$new_port" -gt 65535 ]; then
        error "无效端口号: $new_port (有效范围: 1-65535)"
        exit 1
    fi

    if ! is_mysql_installed; then
        error "MySQL 未安装，请先执行: $0 install"
        exit 1
    fi

    # 检查当前端口
    local current_port
    current_port=$(get_mysql_port)
    if [ "$current_port" = "$new_port" ]; then
        info "MySQL 已在监听端口 $new_port，无需修改"
        return 0
    fi

    # 检查端口是否被占用
    if ss -tlnp 2>/dev/null | grep -q ":${new_port} "; then
        local occupier
        occupier=$(ss -tlnp 2>/dev/null | grep ":${new_port} " | awk '{print $6}')
        error "端口 $new_port 已被占用: $occupier"
        exit 1
    fi

    # 处理防火墙：放开新端口，移除旧端口
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        info "防火墙: 放开新端口 ${new_port}/tcp..."
        firewall-cmd --permanent --add-port="${new_port}/tcp" 2>/dev/null || true
        info "防火墙: 移除旧端口 ${current_port}/tcp..."
        firewall-cmd --permanent --remove-port="${current_port}/tcp" 2>/dev/null || true
        firewall-cmd --reload 2>/dev/null || true
        info "防火墙: 规则已更新并重载"
    fi

    # 备份当前端口配置（用于回滚）
    local port_conf="${MYSQL_CONF_DIR}/my-panel-port.cnf"
    local port_conf_bak="${MYSQL_CONF_DIR}/my-panel-port.cnf.bak"
    [ -f "$port_conf" ] && cp -f "$port_conf" "$port_conf_bak"

    # 写入配置
    cat > "$port_conf" <<EOF
[mysqld]
port=${new_port}

[client]
port=${new_port}
EOF
    chown mysql:mysql "$port_conf"

    info "端口配置已写入: $port_conf"
    info "旧端口: ${current_port} -> 新端口: $new_port"

    # 重启 MySQL 使配置生效
    if is_mysql_running; then
        if restart_mysql; then
            # 验证新端口
            sleep 2
            local verify_port
            verify_port=$(ss -tlnp 2>/dev/null | grep mysqld | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
            if [ "$verify_port" = "$new_port" ]; then
                info "端口修改成功，当前监听端口: $new_port"
            else
                warn "端口验证结果: ${verify_port:-未检测到}，请检查 MySQL 是否正常启动"
            fi
        else
            # 重启失败，回滚配置
            error "MySQL 重启失败，回滚端口配置..."
            if [ -f "$port_conf_bak" ]; then
                mv -f "$port_conf_bak" "$port_conf"
            else
                rm -f "$port_conf"
            fi
            warn "已回滚端口配置，尝试用旧端口恢复 MySQL..."
            systemctl start "$MYSQL_SERVICE" 2>/dev/null || true
            if is_mysql_running; then
                info "MySQL 已用旧端口 ${current_port} 恢复"
            else
                error "MySQL 恢复失败，请手动检查日志: ${MYSQL_LOG_DIR}/error.log"
            fi
            exit 1
        fi
    else
        info "MySQL 未运行，下次启动时将使用新端口 $new_port"
    fi

    # 清理备份
    rm -f "$port_conf_bak"
}

# ─── 卸载 MySQL ───
uninstall_mysql() {
    check_root

    warn "即将卸载 MySQL，所有数据将丢失！"
    read -p "确认卸载？输入 YES 确认: " confirm
    if [ "$confirm" != "YES" ]; then
        info "已取消卸载"
        return 0
    fi

    stop_mysql
    disable_mysql

    info "卸载 MySQL 包..."
    dnf remove -y mysql-server mysql mysql-common 2>/dev/null || true

    info "清理数据目录..."
    rm -rf "$MYSQL_DATA_DIR"
    rm -rf "$MYSQL_LOG_DIR"
    rm -f "${MYSQL_CONF_DIR}/my-panel.cnf"

    info "MySQL 已完全卸载"
}

# ─── 使用帮助 ───
show_help() {
    cat <<EOF
MySQL 8 一键管理脚本

用法: $0 <命令>

命令:
  install     安装 MySQL 8 并完成初始化配置
  start       启动 MySQL
  stop        停止 MySQL
  restart     重启 MySQL
  status      查看 MySQL 状态
  conn        显示 MySQL 连接信息（IP、端口、连接串）
  setport     修改 MySQL 监听端口，用法: $0 setport <端口号>
  enable      设置开机自启
  disable     取消开机自启
  uninstall   完全卸载 MySQL（含数据）

环境变量:
  MYSQL_ROOT_PASSWORD  root 密码 (默认: MyPanel@2026)
  MYSQL_DATA_DIR       数据目录 (默认: /var/lib/mysql)

示例:
  $0 install                          # 安装并初始化
  MYSQL_ROOT_PASSWORD=NewPass123 $0 install  # 指定密码安装
  $0 status                           # 查看状态
  $0 conn                             # 查看连接信息
  $0 setport 3307                     # 修改监听端口为 3307
  $0 restart                          # 重启
EOF
}

# ─── 主入口 ───
case "${1:-}" in
    install)
        install_mysql
        ;;
    start)
        start_mysql
        ;;
    stop)
        stop_mysql
        ;;
    restart)
        restart_mysql
        ;;
    status)
        show_status
        ;;
    conn)
        show_conn
        ;;
    setport)
        setport_mysql "$@"
        ;;
    enable)
        enable_mysql
        ;;
    disable)
        disable_mysql
        ;;
    uninstall)
        uninstall_mysql
        ;;
    *)
        show_help
        exit 1
        ;;
esac
