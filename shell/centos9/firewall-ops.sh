#!/bin/bash

# =============================================
#  Firewall 防火墙运维脚本 (CentOS 9 / RHEL 9)
#  功能: 启停 / 端口管理 / 服务管理 / IP管理 / 区域管理 / 状态查看
# chmod +x firewall-ops.sh
# sudo ./firewall-ops.sh
# =============================================

# ─── 颜色定义 ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }
title() { echo -e "\n${CYAN}===== $* =====${NC}"; }

# ─── 权限检查 ───
check_root() {
    if [ "$EUID" -ne 0 ]; then
        error "请使用 root 用户执行此脚本"
        exit 1
    fi
}

# ─── 检查 firewalld 是否可用 ───
check_firewalld() {
    if ! rpm -q firewalld &>/dev/null; then
        error "firewalld 未安装"
        read -rp "是否现在安装？[y/N]: " ans
        if [[ "$ans" =~ ^[Yy]$ ]]; then
            dnf install -y firewalld
            systemctl enable firewalld
            systemctl start firewalld
            info "firewalld 安装并启动成功"
        else
            exit 1
        fi
    fi
}

# ─── 读取非空输入 ───
read_nonempty() {
    local prompt="$1"
    local var_name="$2"
    local value
    while true; do
        read -rp "$prompt" value
        [ -n "$value" ] && eval "$var_name='$value'" && return 0
        warn "输入不能为空"
    done
}

# ─── 读取确认 ───
read_confirm() {
    local prompt="$1"
    read -rp "$prompt [y/N]: " answer
    [[ "$answer" =~ ^[Yy]$ ]]
}

# ─── 暂停 ───
pause() {
    echo ""
    read -rn 1 -s -p "按任意键继续..."
    echo ""
}

# ─── 获取当前默认区域 ───
get_default_zone() {
    firewall-cmd --get-default-zone 2>/dev/null || echo "public"
}

# =============================================
#  防火墙启停
# =============================================

fw_start() {
    title "启动防火墙"
    check_firewalld
    systemctl start firewalld
    systemctl enable firewalld
    info "防火墙已启动并设为开机自启"
    fw_status_brief
}

fw_stop() {
    title "停止防火墙"
    if ! read_confirm "确认停止防火墙？"; then
        info "已取消"; return
    fi
    systemctl stop firewalld
    systemctl disable firewalld
    warn "防火墙已停止并取消开机自启"
}

fw_restart() {
    title "重启防火墙"
    systemctl restart firewalld
    info "防火墙已重启"
    fw_status_brief
}

fw_reload() {
    title "重载防火墙配置"
    firewall-cmd --reload
    info "配置已重载（不中断现有连接）"
}

# =============================================
#  状态查看
# =============================================

fw_status_brief() {
    title "防火墙状态"
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        info "运行状态: 运行中"
    else
        warn "运行状态: 未运行"
    fi

    if systemctl is-enabled --quiet firewalld 2>/dev/null; then
        info "开机自启: 已启用"
    else
        warn "开机自启: 未启用"
    fi

    local default_zone
    default_zone=$(get_default_zone)
    info "默认区域: ${default_zone}"
}

fw_status_detail() {
    title "防火墙详细信息"
    echo ""
    echo "--- 默认区域 ---"
    firewall-cmd --get-default-zone
    echo ""
    echo "--- 所有区域 ---"
    firewall-cmd --get-zones
    echo ""
    echo "--- 活跃区域 ---"
    firewall-cmd --get-active-zones
    echo ""
    echo "--- 默认区域详情 ---"
    firewall-cmd --list-all
}

fw_status_services() {
    title "已开放的服务"
    local zone
    zone=$(get_default_zone)
    echo "区域: ${zone}"
    echo ""
    local services
    services=$(firewall-cmd --zone="$zone" --list-services 2>/dev/null)
    if [ -n "$services" ]; then
        for svc in $services; do
            local ports_info
            ports_info=$(firewall-cmd --service="$svc" --get-ports 2>/dev/null)
            if [ -n "$ports_info" ]; then
                echo "  ${svc}  ->  端口: ${ports_info}"
            else
                echo "  ${svc}"
            fi
        done
    else
        warn "无已开放的服务"
    fi
}

fw_status_ports() {
    title "已开放的端口"
    local zone
    zone=$(get_default_zone)
    echo "区域: ${zone}"
    echo ""
    local ports
    ports=$(firewall-cmd --zone="$zone" --list-ports 2>/dev/null)
    if [ -n "$ports" ]; then
        for p in $ports; do
            echo "  ${p}"
        done
    else
        warn "无已开放的端口"
    fi
}

# =============================================
#  端口管理
# =============================================

port_open() {
    title "开放端口"
    local port protocol zone permanent

    read_nonempty "端口号 (支持范围如 8000-8100): " port
    echo "协议类型:"
    echo "  1) tcp"
    echo "  2) udp"
    echo "  3) tcp+udp"
    read -rp "请选择 [1-3, 默认1]: " proto_choice

    case "$proto_choice" in
        2) protocol="udp" ;;
        3) protocol="tcp udp" ;;
        *) protocol="tcp" ;;
    esac

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    read -rp "是否永久生效？[Y/n]: " perm_ans
    permanent=true
    [[ "$perm_ans" =~ ^[Nn]$ ]] && permanent=false

    for proto in $protocol; do
        if [ "$permanent" = true ]; then
            firewall-cmd --zone="$zone" --permanent --add-port="${port}/${proto}"
            firewall-cmd --zone="$zone" --add-port="${port}/${proto}"
        else
            firewall-cmd --zone="$zone" --add-port="${port}/${proto}"
            warn "仅临时生效，重启后失效"
        fi
        info "已开放端口: ${port}/${proto} (区域: ${zone})"
    done

    if [ "$permanent" = true ]; then
        info "已永久生效"
    fi
}

port_close() {
    title "关闭端口"
    local port protocol zone

    # 先显示当前端口
    fw_status_ports
    echo ""

    read_nonempty "要关闭的端口号: " port
    echo "协议类型:"
    echo "  1) tcp"
    echo "  2) udp"
    echo "  3) tcp+udp"
    read -rp "请选择 [1-3, 默认1]: " proto_choice

    case "$proto_choice" in
        2) protocol="udp" ;;
        3) protocol="tcp udp" ;;
        *) protocol="tcp" ;;
    esac

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    for proto in $protocol; do
        firewall-cmd --zone="$zone" --permanent --remove-port="${port}/${proto}" 2>/dev/null || true
        firewall-cmd --zone="$zone" --remove-port="${port}/${proto}" 2>/dev/null || true
        info "已关闭端口: ${port}/${proto}"
    done
}

port_batch_open() {
    title "批量开放端口"
    echo "输入要开放的端口列表（空格分隔），例如: 80 443 8080 3306 6379"
    read -rp "端口列表: " ports

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    echo "协议类型: 1)tcp  2)udp  3)tcp+udp"
    read -rp "请选择 [1-3, 默认1]: " proto_choice
    case "$proto_choice" in
        2) protocol="udp" ;;
        3) protocol="tcp udp" ;;
        *) protocol="tcp" ;;
    esac

    for p in $ports; do
        for proto in $protocol; do
            firewall-cmd --zone="$zone" --permanent --add-port="${p}/${proto}" 2>/dev/null
            firewall-cmd --zone="$zone" --add-port="${p}/${proto}" 2>/dev/null
        done
        info "已开放: ${p}/${protocol}"
    done
    info "批量开放完成"
}

# =============================================
#  服务管理
# =============================================

service_open() {
    title "开放服务"
    echo "可用服务列表:"
    firewall-cmd --get-services | tr ' ' '\n' | column -t
    echo ""

    local service_name zone
    read_nonempty "服务名 (如 http, https, ssh, mysql, redis): " service_name

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    read -rp "是否永久生效？[Y/n]: " perm_ans
    permanent=true
    [[ "$perm_ans" =~ ^[Nn]$ ]] && permanent=false

    if [ "$permanent" = true ]; then
        firewall-cmd --zone="$zone" --permanent --add-service="$service_name"
        firewall-cmd --zone="$zone" --add-service="$service_name"
    else
        firewall-cmd --zone="$zone" --add-service="$service_name"
        warn "仅临时生效"
    fi
    info "已开放服务: ${service_name} (区域: ${zone})"
}

service_close() {
    title "关闭服务"
    fw_status_services
    echo ""

    local service_name zone
    read_nonempty "要关闭的服务名: " service_name

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    firewall-cmd --zone="$zone" --permanent --remove-service="$service_name" 2>/dev/null || true
    firewall-cmd --zone="$zone" --remove-service="$service_name" 2>/dev/null || true
    info "已关闭服务: ${service_name}"
}

# =============================================
#  IP 管理
# =============================================

ip_allow() {
    title "允许 IP 访问"
    local ip zone

    read_nonempty "IP 地址或网段 (如 192.168.1.100 或 192.168.1.0/24): " ip

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    echo "规则类型:"
    echo "  1) 允许所有端口"
    echo "  2) 允许指定端口"
    read -rp "请选择 [1-2]: " rule_choice

    if [ "$rule_choice" = "2" ]; then
        local port proto
        read_nonempty "端口号: " port
        read -rp "协议 [默认tcp]: " proto
        proto="${proto:-tcp}"

        firewall-cmd --zone="$zone" --permanent --add-rich-rule="rule family='ipv4' source address='${ip}' port port='${port}' protocol='${proto}' accept"
        firewall-cmd --zone="$zone" --add-rich-rule="rule family='ipv4' source address='${ip}' port port='${port}' protocol='${proto}' accept"
        info "已允许 ${ip} 访问端口 ${port}/${proto}"
    else
        firewall-cmd --zone="$zone" --permanent --add-source="${ip}"
        firewall-cmd --zone="$zone" --add-source="${ip}"
        info "已允许 ${ip} 访问所有端口 (区域: ${zone})"
    fi
}

ip_deny() {
    title "禁止 IP 访问"
    local ip zone

    read_nonempty "IP 地址或网段: " ip

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    echo "规则类型:"
    echo "  1) 禁止所有访问"
    echo "  2) 禁止指定端口"
    read -rp "请选择 [1-2]: " rule_choice

    if [ "$rule_choice" = "2" ]; then
        local port proto
        read_nonempty "端口号: " port
        read -rp "协议 [默认tcp]: " proto
        proto="${proto:-tcp}"

        firewall-cmd --zone="$zone" --permanent --add-rich-rule="rule family='ipv4' source address='${ip}' port port='${port}' protocol='${proto}' reject"
        firewall-cmd --zone="$zone" --add-rich-rule="rule family='ipv4' source address='${ip}' port port='${port}' protocol='${proto}' reject"
        info "已禁止 ${ip} 访问端口 ${port}/${proto}"
    else
        firewall-cmd --zone="$zone" --permanent --add-rich-rule="rule family='ipv4' source address='${ip}' reject"
        firewall-cmd --zone="$zone" --add-rich-rule="rule family='ipv4' source address='${ip}' reject"
        info "已禁止 ${ip} 的所有访问"
    fi
}

ip_list() {
    title "IP 规则列表"
    local zone
    zone=$(get_default_zone)
    echo "区域: ${zone}"
    echo ""

    echo "--- 白名单 IP (source) ---"
    local sources
    sources=$(firewall-cmd --zone="$zone" --list-sources 2>/dev/null)
    [ -n "$sources" ] && echo "  $sources" || echo "  (无)"

    echo ""
    echo "--- Rich Rules ---"
    local rules
    rules=$(firewall-cmd --zone="$zone" --list-rich-rules 2>/dev/null)
    [ -n "$rules" ] && echo "$rules" | sed 's/^/  /' || echo "  (无)"
}

ip_remove_rule() {
    title "删除 IP 规则"
    ip_list
    echo ""
    echo "请粘贴要删除的完整规则:"
    read -rp "规则: " rule

    local zone
    zone=$(get_default_zone)

    firewall-cmd --zone="$zone" --permanent --remove-rich-rule="$rule" 2>/dev/null || true
    firewall-cmd --zone="$zone" --remove-rich-rule="$rule" 2>/dev/null || true
    info "规则已删除"
}

# =============================================
#  区域管理
# =============================================

zone_list() {
    title "区域列表"
    echo "--- 所有区域 ---"
    firewall-cmd --get-zones | tr ' ' '\n' | column -t
    echo ""
    echo "--- 活跃区域 ---"
    firewall-cmd --get-active-zones
    echo ""
    echo "--- 默认区域 ---"
    firewall-cmd --get-default-zone
}

zone_set_default() {
    title "设置默认区域"
    echo "可用区域:"
    firewall-cmd --get-zones | tr ' ' '\n' | column -t
    echo ""

    local zone
    read_nonempty "新的默认区域: " zone

    firewall-cmd --set-default-zone="$zone"
    info "默认区域已设置为: ${zone}"
}

zone_detail() {
    title "查看区域详情"
    local zone
    read -rp "区域名 [默认: $(get_default_zone)]: " zone
    zone="${zone:-$(get_default_zone)}"
    firewall-cmd --zone="$zone" --list-all
}

# =============================================
#  端口转发
# =============================================

port_forward_add() {
    title "添加端口转发"
    local from_port to_ip to_port proto zone

    read_nonempty "本地监听端口: " from_port
    read_nonempty "目标 IP: " to_ip
    read_nonempty "目标端口: " to_port
    read -rp "协议 [默认tcp]: " proto
    proto="${proto:-tcp}"

    local default_zone
    default_zone=$(get_default_zone)
    read -rp "区域 [默认: ${default_zone}]: " zone
    zone="${zone:-$default_zone}"

    # 开启 masquerade
    firewall-cmd --zone="$zone" --permanent --add-masquerade 2>/dev/null || true
    firewall-cmd --zone="$zone" --add-masquerade 2>/dev/null || true

    firewall-cmd --zone="$zone" --permanent --add-forward-port="port=${from_port}:proto=${proto}:toaddr=${to_ip}:toport=${to_port}"
    firewall-cmd --zone="$zone" --add-forward-port="port=${from_port}:proto=${proto}:toaddr=${to_ip}:toport=${to_port}"

    info "端口转发已添加: ${from_port} -> ${to_ip}:${to_port} (${proto})"
}

port_forward_remove() {
    title "删除端口转发"
    echo "当前转发规则:"
    local zone
    zone=$(get_default_zone)
    firewall-cmd --zone="$zone" --list-forward-ports 2>/dev/null || echo "  (无)"
    echo ""

    local from_port to_ip to_port proto
    read_nonempty "本地监听端口: " from_port
    read_nonempty "目标 IP: " to_ip
    read_nonempty "目标端口: " to_port
    read -rp "协议 [默认tcp]: " proto
    proto="${proto:-tcp}"

    firewall-cmd --zone="$zone" --permanent --remove-forward-port="port=${from_port}:proto=${proto}:toaddr=${to_ip}:toport=${to_port}" 2>/dev/null || true
    firewall-cmd --zone="$zone" --remove-forward-port="port=${from_port}:proto=${proto}:toaddr=${to_ip}:toport=${to_port}" 2>/dev/null || true
    info "端口转发已删除"
}

# =============================================
#  常用预设
# =============================================

preset_web() {
    title "预设: Web 服务器"
    local zone
    zone=$(get_default_zone)
    firewall-cmd --zone="$zone" --permanent --add-service=http
    firewall-cmd --zone="$zone" --permanent --add-service=https
    firewall-cmd --zone="$zone" --add-service=http
    firewall-cmd --zone="$zone" --add-service=https
    info "已开放 HTTP(80) + HTTPS(443)"
}

preset_mysql() {
    title "预设: MySQL"
    local zone port
    zone=$(get_default_zone)
    read -rp "端口 [默认3306]: " port
    port="${port:-3306}"
    firewall-cmd --zone="$zone" --permanent --add-port="${port}/tcp"
    firewall-cmd --zone="$zone" --add-port="${port}/tcp"
    info "已开放 MySQL 端口 ${port}/tcp"
}

preset_redis() {
    title "预设: Redis"
    local zone port
    zone=$(get_default_zone)
    read -rp "端口 [默认6379]: " port
    port="${port:-6379}"
    firewall-cmd --zone="$zone" --permanent --add-port="${port}/tcp"
    firewall-cmd --zone="$zone" --add-port="${port}/tcp"
    info "已开放 Redis 端口 ${port}/tcp"
}

preset_app() {
    title "预设: 应用服务器 (SSH + HTTP + HTTPS + 自定义端口)"
    local zone
    zone=$(get_default_zone)
    firewall-cmd --zone="$zone" --permanent --add-service=ssh
    firewall-cmd --zone="$zone" --permanent --add-service=http
    firewall-cmd --zone="$zone" --permanent --add-service=https
    firewall-cmd --zone="$zone" --add-service=ssh
    firewall-cmd --zone="$zone" --add-service=http
    firewall-cmd --zone="$zone" --add-service=https
    info "已开放 SSH(22) + HTTP(80) + HTTPS(443)"

    echo ""
    read -rp "是否额外开放自定义端口？[y/N]: " ans
    if [[ "$ans" =~ ^[Yy]$ ]]; then
        read -rp "端口列表 (空格分隔): " ports
        for p in $ports; do
            firewall-cmd --zone="$zone" --permanent --add-port="${p}/tcp"
            firewall-cmd --zone="$zone" --add-port="${p}/tcp"
            info "已开放: ${p}/tcp"
        done
    fi
}

preset_reset() {
    title "重置防火墙规则"
    warn "将清除所有自定义规则！"
    if ! read_confirm "确认重置？"; then
        info "已取消"; return
    fi

    local zone
    zone=$(get_default_zone)

    # 移除所有端口
    for p in $(firewall-cmd --zone="$zone" --list-ports 2>/dev/null); do
        firewall-cmd --zone="$zone" --permanent --remove-port="$p" 2>/dev/null || true
        firewall-cmd --zone="$zone" --remove-port="$p" 2>/dev/null || true
    done

    # 移除所有服务（保留 ssh）
    for s in $(firewall-cmd --zone="$zone" --list-services 2>/dev/null); do
        if [ "$s" != "ssh" ]; then
            firewall-cmd --zone="$zone" --permanent --remove-service="$s" 2>/dev/null || true
            firewall-cmd --zone="$zone" --remove-service="$s" 2>/dev/null || true
        fi
    done

    # 移除所有 rich rules
    for r in $(firewall-cmd --zone="$zone" --list-rich-rules 2>/dev/null); do
        firewall-cmd --zone="$zone" --permanent --remove-rich-rule="$r" 2>/dev/null || true
        firewall-cmd --zone="$zone" --remove-rich-rule="$r" 2>/dev/null || true
    done

    # 移除所有 source
    for s in $(firewall-cmd --zone="$zone" --list-sources 2>/dev/null); do
        firewall-cmd --zone="$zone" --permanent --remove-source="$s" 2>/dev/null || true
        firewall-cmd --zone="$zone" --remove-source="$s" 2>/dev/null || true
    done

    # 移除端口转发
    for f in $(firewall-cmd --zone="$zone" --list-forward-ports 2>/dev/null); do
        firewall-cmd --zone="$zone" --permanent --remove-forward-port="$f" 2>/dev/null || true
        firewall-cmd --zone="$zone" --remove-forward-port="$f" 2>/dev/null || true
    done

    firewall-cmd --reload
    info "防火墙已重置（保留 SSH 服务）"
}

# =============================================
#  菜单
# =============================================

menu_start_stop() {
    while true; do
        title "防火墙启停"
        echo "  1) 启动防火墙 (并设为开机自启)"
        echo "  2) 停止防火墙 (并取消开机自启)"
        echo "  3) 重启防火墙"
        echo "  4) 重载配置 (不中断连接)"
        echo "  0) 返回上级"
        read -rp "请选择 [0-4]: " choice

        case "$choice" in
            1) fw_start ;;
            2) fw_stop ;;
            3) fw_restart ;;
            4) fw_reload ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

menu_status() {
    while true; do
        title "状态查看"
        echo "  1) 简要状态"
        echo "  2) 详细信息"
        echo "  3) 已开放的服务"
        echo "  4) 已开放的端口"
        echo "  5) 监听端口 (ss)"
        echo "  0) 返回上级"
        read -rp "请选择 [0-5]: " choice

        case "$choice" in
            1) fw_status_brief ;;
            2) fw_status_detail ;;
            3) fw_status_services ;;
            4) fw_status_ports ;;
            5) ss -tlnp | column -t ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

menu_port() {
    while true; do
        title "端口管理"
        echo "  1) 开放端口"
        echo "  2) 关闭端口"
        echo "  3) 批量开放端口"
        echo "  4) 查看已开放端口"
        echo "  0) 返回上级"
        read -rp "请选择 [0-4]: " choice

        case "$choice" in
            1) port_open ;;
            2) port_close ;;
            3) port_batch_open ;;
            4) fw_status_ports ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

menu_service() {
    while true; do
        title "服务管理"
        echo "  1) 开放服务"
        echo "  2) 关闭服务"
        echo "  3) 查看已开放服务"
        echo "  4) 查看所有可用服务"
        echo "  0) 返回上级"
        read -rp "请选择 [0-4]: " choice

        case "$choice" in
            1) service_open ;;
            2) service_close ;;
            3) fw_status_services ;;
            4) firewall-cmd --get-services | tr ' ' '\n' | column -t ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

menu_ip() {
    while true; do
        title "IP 管理"
        echo "  1) 允许 IP 访问"
        echo "  2) 禁止 IP 访问"
        echo "  3) 查看 IP 规则"
        echo "  4) 删除 IP 规则"
        echo "  0) 返回上级"
        read -rp "请选择 [0-4]: " choice

        case "$choice" in
            1) ip_allow ;;
            2) ip_deny ;;
            3) ip_list ;;
            4) ip_remove_rule ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

menu_zone() {
    while true; do
        title "区域管理"
        echo "  1) 查看区域列表"
        echo "  2) 设置默认区域"
        echo "  3) 查看区域详情"
        echo "  0) 返回上级"
        read -rp "请选择 [0-3]: " choice

        case "$choice" in
            1) zone_list ;;
            2) zone_set_default ;;
            3) zone_detail ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

menu_forward() {
    while true; do
        title "端口转发"
        echo "  1) 添加端口转发"
        echo "  2) 删除端口转发"
        echo "  3) 查看转发规则"
        echo "  0) 返回上级"
        read -rp "请选择 [0-3]: " choice

        case "$choice" in
            1) port_forward_add ;;
            2) port_forward_remove ;;
            3)
                local zone
                zone=$(get_default_zone)
                firewall-cmd --zone="$zone" --list-forward-ports 2>/dev/null || echo "(无)"
                ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

menu_preset() {
    while true; do
        title "常用预设"
        echo "  1) Web 服务器 (HTTP + HTTPS)"
        echo "  2) MySQL"
        echo "  3) Redis"
        echo "  4) 应用服务器 (SSH + HTTP + HTTPS + 自定义)"
        echo "  5) 重置所有规则 (保留 SSH)"
        echo "  0) 返回上级"
        read -rp "请选择 [0-5]: " choice

        case "$choice" in
            1) preset_web ;;
            2) preset_mysql ;;
            3) preset_redis ;;
            4) preset_app ;;
            5) preset_reset ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

main_menu() {
    check_root
    check_firewalld

    while true; do
        clear
        echo ""
        echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║     Firewall 防火墙运维管理工具      ║${NC}"
        echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
        echo ""

        # 简要状态
        if systemctl is-active --quiet firewalld 2>/dev/null; then
            info "状态: 运行中  默认区域: $(get_default_zone)"
        else
            warn "状态: 未运行"
        fi
        echo ""

        echo "  1) 防火墙启停"
        echo "  2) 状态查看"
        echo "  3) 端口管理"
        echo "  4) 服务管理"
        echo "  5) IP 管理 (白名单/黑名单)"
        echo "  6) 区域管理"
        echo "  7) 端口转发"
        echo "  8) 常用预设"
        echo "  0) 退出"
        echo ""
        read -rp "请选择 [0-8]: " choice

        case "$choice" in
            1) menu_start_stop ;;
            2) menu_status ;;
            3) menu_port ;;
            4) menu_service ;;
            5) menu_ip ;;
            6) menu_zone ;;
            7) menu_forward ;;
            8) menu_preset ;;
            0) info "再见!"; exit 0 ;;
            *) warn "无效选择" ;;
        esac
    done
}

main_menu
