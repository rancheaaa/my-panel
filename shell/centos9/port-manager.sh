#!/bin/bash

# =============================================
#  端口管理脚本 (CentOS 9 / RHEL 9)
#  功能: 开放/屏蔽端口、查看端口状态、协议管理
# chmod +x port-manager.sh
#
# sudo ./port-manager.sh open 80 tcp
# sudo ./port-manager.sh close 80 tcp
# sudo ./port-manager.sh list
# sudo ./port-manager.sh status
# sudo ./port-manager.sh
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

# ─── 确保 firewalld 可用 ───
ensure_firewalld() {
    if ! rpm -q firewalld &>/dev/null; then
        error "firewalld 未安装，正在安装..."
        dnf install -y firewalld || { error "安装 firewalld 失败"; exit 1; }
        systemctl enable firewalld
        systemctl start firewalld
    fi
    if ! systemctl is-active --quiet firewalld 2>/dev/null; then
        warn "firewalld 未运行，正在启动..."
        systemctl start firewalld || { error "启动 firewalld 失败"; exit 1; }
    fi
}

# ─── 获取默认区域 ───
get_default_zone() {
    firewall-cmd --get-default-zone 2>/dev/null || echo "public"
}

# ─── 解析服务名，返回 firewalld 服务名；非服务名返回空 ───
# 仅映射服务名，tcp/udp 等协议名不在此处理
resolve_service() {
    local input="${1,,}"  # 转小写
    case "$input" in
        http)      echo "http" ;;
        https)     echo "https" ;;
        ssh)       echo "ssh" ;;
        ftp)       echo "ftp" ;;
        dns)       echo "dns" ;;
        smtp)      echo "smtp" ;;
        smtps)     echo "smtps" ;;
        imap)      echo "imap" ;;
        imaps)     echo "imaps" ;;
        pop3)      echo "pop3" ;;
        pop3s)     echo "pop3s" ;;
        mysql)     echo "mysql" ;;
        redis)     echo "redis" ;;
        postgresql|pg) echo "postgresql" ;;
        *)         echo "" ;;
    esac
}

# ─── 验证端口号 ───
validate_port() {
    local port="$1"
    if [ -z "$port" ]; then
        error "端口号不能为空"
        return 1
    fi
    if ! [[ "$port" =~ ^[0-9]+$ ]] || [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
        error "无效端口号: $port (有效范围: 1-65535)"
        return 1
    fi
    return 0
}

# ─── 验证协议 ───
validate_proto() {
    local proto="$1"
    case "$proto" in
        tcp|udp) return 0 ;;
        *)
            error "无效协议: ${proto} (仅支持 tcp/udp)"
            return 1
            ;;
    esac
}

# ─── 检查端口是否已在防火墙开放 ───
is_port_open_in_fw() {
    local port="$1"
    local proto="$2"
    local zone="$3"
    firewall-cmd --zone="$zone" --list-ports 2>/dev/null | grep -qw "${port}/${proto}"
}

# ─── 检查服务是否已在防火墙开放 ───
is_service_open_in_fw() {
    local service="$1"
    local zone="$2"
    firewall-cmd --zone="$zone" --list-services 2>/dev/null | grep -qw "$service"
}

# ─── 开放端口 ───
# 用法: port_open <端口> <协议(tcp/udp)> [区域] [永久(true/false)]
port_open() {
    local port="$1"
    local proto="$2"
    local zone="${3:-$(get_default_zone)}"
    local permanent="${4:-true}"

    validate_port "$port" || return 1
    validate_proto "$proto" || return 1

    # 检查是否已开放
    if is_port_open_in_fw "$port" "$proto" "$zone"; then
        warn "端口 ${port}/${proto} 已在区域 ${zone} 中开放，跳过"
        return 0
    fi

    # 永久 + 运行时
    if [ "$permanent" = "true" ]; then
        if ! firewall-cmd --zone="$zone" --permanent --add-port="${port}/${proto}" 2>&1; then
            error "永久开放端口 ${port}/${proto} 失败"
            return 1
        fi
        if ! firewall-cmd --zone="$zone" --add-port="${port}/${proto}" 2>&1; then
            error "运行时开放端口 ${port}/${proto} 失败"
            return 1
        fi
        info "已开放端口: ${port}/${proto} (区域: ${zone}) [永久]"
    else
        if ! firewall-cmd --zone="$zone" --add-port="${port}/${proto}" 2>&1; then
            error "开放端口 ${port}/${proto} 失败"
            return 1
        fi
        info "已开放端口: ${port}/${proto} (区域: ${zone}) [临时]"
    fi
}

# ─── 开放服务 ───
# 用法: service_open <服务名> [区域] [永久(true/false)]
service_open() {
    local service="$1"
    local zone="${2:-$(get_default_zone)}"
    local permanent="${3:-true}"

    if [ -z "$service" ]; then
        error "服务名不能为空"
        return 1
    fi

    # 检查是否已开放
    if is_service_open_in_fw "$service" "$zone"; then
        warn "服务 ${service} 已在区域 ${zone} 中开放，跳过"
        return 0
    fi

    # 验证服务是否存在
    if ! firewall-cmd --get-services 2>/dev/null | grep -qw "$service"; then
        error "未知服务: ${service}，可用服务请执行: firewall-cmd --get-services"
        return 1
    fi

    if [ "$permanent" = "true" ]; then
        if ! firewall-cmd --zone="$zone" --permanent --add-service="$service" 2>&1; then
            error "永久开放服务 ${service} 失败"
            return 1
        fi
        if ! firewall-cmd --zone="$zone" --add-service="$service" 2>&1; then
            error "运行时开放服务 ${service} 失败"
            return 1
        fi
        info "已开放服务: ${service} (区域: ${zone}) [永久]"
    else
        if ! firewall-cmd --zone="$zone" --add-service="$service" 2>&1; then
            error "开放服务 ${service} 失败"
            return 1
        fi
        info "已开放服务: ${service} (区域: ${zone}) [临时]"
    fi
}

# ─── 智能开放（自动判断是服务名还是端口） ───
smart_open() {
    local input="$1"
    local proto="$2"
    local zone="$3"
    local permanent="$4"

    # 先尝试解析为服务名
    local service
    service=$(resolve_service "$input")
    if [ -n "$service" ]; then
        service_open "$service" "$zone" "$permanent"
        return $?
    fi

    # 不是服务名，当作端口号处理
    port_open "$input" "${proto:-tcp}" "$zone" "$permanent"
}

# ─── 屏蔽端口 ───
port_close() {
    local port="$1"
    local proto="$2"
    local zone="${3:-$(get_default_zone)}"

    validate_port "$port" || return 1
    validate_proto "$proto" || return 1

    # 检查是否已关闭
    if ! is_port_open_in_fw "$port" "$proto" "$zone"; then
        warn "端口 ${port}/${proto} 未在区域 ${zone} 中开放，跳过"
        return 0
    fi

    firewall-cmd --zone="$zone" --permanent --remove-port="${port}/${proto}" 2>&1 || true
    firewall-cmd --zone="$zone" --remove-port="${port}/${proto}" 2>&1 || true
    info "已屏蔽端口: ${port}/${proto} (区域: ${zone})"
}

# ─── 屏蔽服务 ───
service_close() {
    local service="$1"
    local zone="${2:-$(get_default_zone)}"

    if [ -z "$service" ]; then
        error "服务名不能为空"
        return 1
    fi

    if ! is_service_open_in_fw "$service" "$zone"; then
        warn "服务 ${service} 未在区域 ${zone} 中开放，跳过"
        return 0
    fi

    firewall-cmd --zone="$zone" --permanent --remove-service="$service" 2>&1 || true
    firewall-cmd --zone="$zone" --remove-service="$service" 2>&1 || true
    info "已屏蔽服务: ${service} (区域: ${zone})"
}

# ─── 智能屏蔽 ───
smart_close() {
    local input="$1"
    local proto="$2"
    local zone="$3"

    local service
    service=$(resolve_service "$input")
    if [ -n "$service" ]; then
        service_close "$service" "$zone"
        return $?
    fi

    port_close "$input" "${proto:-tcp}" "$zone"
}

# ─── 列出已开放端口 ───
port_list() {
    local zone="${1:-$(get_default_zone)}"

    title "已开放的端口和服务 (区域: ${zone})"

    echo ""
    echo "--- 服务 ---"
    local services
    services=$(firewall-cmd --zone="$zone" --list-services 2>/dev/null)
    if [ -n "$services" ]; then
        for svc in $services; do
            local ports_info
            ports_info=$(firewall-cmd --service="$svc" --get-ports 2>/dev/null)
            if [ -n "$ports_info" ]; then
                echo "  ${svc}  ->  ${ports_info}"
            else
                echo "  ${svc}"
            fi
        done
    else
        echo "  (无)"
    fi

    echo ""
    echo "--- 端口 ---"
    local ports
    ports=$(firewall-cmd --zone="$zone" --list-ports 2>/dev/null)
    if [ -n "$ports" ]; then
        for p in $ports; do
            echo "  ${p}"
        done
    else
        echo "  (无)"
    fi

    echo ""
    echo "--- Rich Rules ---"
    local rules
    rules=$(firewall-cmd --zone="$zone" --list-rich-rules 2>/dev/null)
    if [ -n "$rules" ]; then
        echo "$rules" | sed 's/^/  /'
    else
        echo "  (无)"
    fi
}

# ─── 查看端口监听状态 ───
port_status() {
    title "系统端口监听状态"
    echo ""
    echo "--- TCP 监听 ---"
    ss -tlnp 2>/dev/null | head -1
    ss -tlnp 2>/dev/null | tail -n +2 | sort -t: -k2 -n
    echo ""
    echo "--- UDP 监听 ---"
    ss -ulnp 2>/dev/null | head -1
    ss -ulnp 2>/dev/null | tail -n +2 | sort -t: -k2 -n
}

# ─── 检查端口是否开放 ───
port_check() {
    local port="$1"
    local proto="${2:-tcp}"
    local zone="${3:-$(get_default_zone)}"

    title "检查端口 ${port}/${proto}"

    # 检查防火墙是否开放
    local fw_open=false
    if is_port_open_in_fw "$port" "$proto" "$zone"; then
        fw_open=true
        info "防火墙: 已开放 (${port}/${proto}, 区域: ${zone})"
    else
        # 检查服务中是否包含该端口
        for svc in $(firewall-cmd --zone="$zone" --list-services 2>/dev/null); do
            if firewall-cmd --service="$svc" --get-ports 2>/dev/null | grep -qw "${port}/${proto}"; then
                fw_open=true
                info "防火墙: 已开放 (服务: ${svc} -> ${port}/${proto}, 区域: ${zone})"
                break
            fi
        done
    fi

    if [ "$fw_open" = false ]; then
        warn "防火墙: 未开放 (${port}/${proto}, 区域: ${zone})"
    fi

    # 检查是否在监听
    if [ "$proto" = "tcp" ]; then
        if ss -tlnp 2>/dev/null | grep -qP ":${port}\b"; then
            local listener
            listener=$(ss -tlnp 2>/dev/null | grep -P ":${port}\b" | head -1 | awk '{print $6}')
            info "监听状态: 已监听 (${listener})"
        else
            warn "监听状态: 未监听 (TCP)"
        fi
    elif [ "$proto" = "udp" ]; then
        if ss -ulnp 2>/dev/null | grep -qP ":${port}\b"; then
            local listener
            listener=$(ss -ulnp 2>/dev/null | grep -P ":${port}\b" | head -1 | awk '{print $6}')
            info "监听状态: 已监听 (${listener})"
        else
            warn "监听状态: 未监听 (UDP)"
        fi
    fi
}

# ─── 批量开放 ───
port_batch_open() {
    local ports="$1"
    local proto="${2:-tcp}"
    local zone="${3:-$(get_default_zone)}"

    if [ -z "$ports" ]; then
        error "端口列表不能为空"
        return 1
    fi

    validate_proto "$proto" || return 1

    local success=0
    local fail=0
    local skip=0
    for p in $ports; do
        if ! validate_port "$p"; then
            fail=$((fail + 1))
            continue
        fi
        if is_port_open_in_fw "$p" "$proto" "$zone"; then
            warn "端口 ${p}/${proto} 已开放，跳过"
            skip=$((skip + 1))
            continue
        fi
        if firewall-cmd --zone="$zone" --permanent --add-port="${p}/${proto}" &>/dev/null && \
           firewall-cmd --zone="$zone" --add-port="${p}/${proto}" &>/dev/null; then
            info "已开放: ${p}/${proto}"
            success=$((success + 1))
        else
            error "开放失败: ${p}/${proto}"
            fail=$((fail + 1))
        fi
    done
    echo ""
    info "完成: 成功 ${success}, 跳过 ${skip}, 失败 ${fail}"
}

# ─── 批量屏蔽 ───
port_batch_close() {
    local ports="$1"
    local proto="${2:-tcp}"
    local zone="${3:-$(get_default_zone)}"

    if [ -z "$ports" ]; then
        error "端口列表不能为空"
        return 1
    fi

    validate_proto "$proto" || return 1

    local success=0
    local fail=0
    local skip=0
    for p in $ports; do
        if ! validate_port "$p"; then
            fail=$((fail + 1))
            continue
        fi
        if ! is_port_open_in_fw "$p" "$proto" "$zone"; then
            warn "端口 ${p}/${proto} 未开放，跳过"
            skip=$((skip + 1))
            continue
        fi
        firewall-cmd --zone="$zone" --permanent --remove-port="${p}/${proto}" &>/dev/null || true
        firewall-cmd --zone="$zone" --remove-port="${p}/${proto}" &>/dev/null || true
        info "已屏蔽: ${p}/${proto}"
        success=$((success + 1))
    done
    echo ""
    info "完成: 成功 ${success}, 跳过 ${skip}, 失败 ${fail}"
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

# ─── 交互式开放端口 ───
interactive_open() {
    title "开放端口"
    echo "输入方式:"
    echo "  1) 按端口号 (如 8080)"
    echo "  2) 按服务名 (如 http, https, mysql, ssh, redis)"
    read -rp "请选择 [1-2]: " input_choice

    case "$input_choice" in
        2)
            echo ""
            echo "可用服务名: http https ssh ftp dns smtp mysql redis postgresql imap pop3"
            read_nonempty "服务名: " svc_name

            local zone
            read -rp "区域 [默认: $(get_default_zone)]: " zone

            smart_open "$svc_name" "" "$zone" "true"
            ;;
        *)
            local port proto
            read_nonempty "端口号 (支持范围如 8000-8100): " port
            echo "协议: 1)tcp  2)udp  3)tcp+udp"
            read -rp "请选择 [1-3, 默认1]: " proto_choice
            case "$proto_choice" in
                2) proto="udp" ;;
                3) proto="tcp udp" ;;
                *) proto="tcp" ;;
            esac

            local zone
            read -rp "区域 [默认: $(get_default_zone)]: " zone

            for p in $proto; do
                port_open "$port" "$p" "$zone" "true"
            done
            ;;
    esac
}

# ─── 交互式屏蔽端口 ───
interactive_close() {
    title "屏蔽端口"
    port_list
    echo ""

    echo "输入方式:"
    echo "  1) 按端口号"
    echo "  2) 按服务名"
    read -rp "请选择 [1-2]: " input_choice

    case "$input_choice" in
        2)
            echo "可用服务名: http https ssh ftp dns smtp mysql redis postgresql imap pop3"
            read_nonempty "服务名: " svc_name

            local zone
            read -rp "区域 [默认: $(get_default_zone)]: " zone

            smart_close "$svc_name" "" "$zone"
            ;;
        *)
            local port proto
            read_nonempty "端口号: " port
            echo "协议: 1)tcp  2)udp  3)tcp+udp"
            read -rp "请选择 [1-3, 默认1]: " proto_choice
            case "$proto_choice" in
                2) proto="udp" ;;
                3) proto="tcp udp" ;;
                *) proto="tcp" ;;
            esac

            local zone
            read -rp "区域 [默认: $(get_default_zone)]: " zone

            for p in $proto; do
                port_close "$port" "$p" "$zone"
            done
            ;;
    esac
}

# ─── 使用帮助 ───
show_help() {
    cat <<EOF
端口管理脚本 (firewalld)

用法: $0 <命令> [参数]

命令:
  open <端口或服务> [协议] [区域]   开放端口或服务 (默认tcp, 永久生效)
  close <端口或服务> [协议] [区域]  屏蔽端口或服务
  list [区域]                       列出已开放的端口和服务
  status                            查看系统端口监听状态
  check <端口> [协议]               检查端口是否开放及监听
  batch-open <端口列表> [协议]      批量开放端口 (空格分隔)
  batch-close <端口列表> [协议]     批量屏蔽端口
  menu                              交互式菜单

协议类型:
  tcp, udp              端口协议 (默认 tcp)

服务名称 (自动映射):
  http https ssh ftp dns smtp mysql redis postgresql

示例:
  $0 open 8080 tcp                    # 开放 8080/tcp
  $0 open 53 udp                      # 开放 53/udp
  $0 open 8000-8100 tcp               # 开放端口范围
  $0 close 8080 tcp                   # 屏蔽 8080/tcp
  $0 open http                        # 开放 HTTP 服务 (80/tcp)
  $0 open https                       # 开放 HTTPS 服务 (443/tcp)
  $0 open mysql                       # 开放 MySQL 服务 (3306/tcp)
  $0 open ssh                         # 开放 SSH 服务 (22/tcp)
  $0 open redis                       # 开放 Redis 服务 (6379/tcp)
  $0 list                             # 列出已开放端口
  $0 status                           # 查看监听状态
  $0 check 3306 tcp                   # 检查 3306/tcp 状态
  $0 batch-open "80 443 8080" tcp     # 批量开放
  $0 menu                             # 交互式菜单
EOF
}

# ─── 交互式菜单 ───
menu() {
    check_root
    ensure_firewalld

    while true; do
        clear
        echo ""
        echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║        端口管理工具 (firewalld)      ║${NC}"
        echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
        echo ""

        local default_zone
        default_zone=$(get_default_zone)
        if systemctl is-active --quiet firewalld 2>/dev/null; then
            info "防火墙: 运行中  默认区域: ${default_zone}"
        else
            warn "防火墙: 未运行"
        fi
        echo ""

        echo "  1) 开放端口"
        echo "  2) 屏蔽端口"
        echo "  3) 批量开放"
        echo "  4) 批量屏蔽"
        echo "  5) 列出已开放端口/服务"
        echo "  6) 检查端口状态"
        echo "  7) 系统监听状态"
        echo "  8) 常用预设"
        echo "  0) 退出"
        echo ""
        read -rp "请选择 [0-8]: " choice

        case "$choice" in
            1) interactive_open ;;
            2) interactive_close ;;
            3)
                read -rp "端口列表 (空格分隔): " ports
                [ -z "$ports" ] && { warn "未输入端口"; continue; }
                read -rp "协议 [默认tcp]: " proto
                proto="${proto:-tcp}"
                port_batch_open "$ports" "$proto"
                ;;
            4)
                read -rp "端口列表 (空格分隔): " ports
                [ -z "$ports" ] && { warn "未输入端口"; continue; }
                read -rp "协议 [默认tcp]: " proto
                proto="${proto:-tcp}"
                port_batch_close "$ports" "$proto"
                ;;
            5) port_list ;;
            6)
                read -rp "端口号: " chk_port
                [ -z "$chk_port" ] && { warn "未输入端口"; continue; }
                read -rp "协议 [默认tcp]: " chk_proto
                port_check "${chk_port}" "${chk_proto:-tcp}"
                ;;
            7) port_status ;;
            8) menu_preset ;;
            0) info "再见!"; exit 0 ;;
            *) warn "无效选择" ;;
        esac
        echo ""
        read -rn 1 -s -p "按任意键继续..."
    done
}

# ─── 常用预设菜单 ───
menu_preset() {
    title "常用预设"
    echo "  1) Web 服务器 (HTTP + HTTPS)"
    echo "  2) MySQL (3306/tcp)"
    echo "  3) Redis (6379/tcp)"
    echo "  4) PostgreSQL (5432/tcp)"
    echo "  5) SSH (22/tcp)"
    echo "  6) FTP (21/tcp + 被动模式端口)"
    echo "  7) DNS (53/tcp + 53/udp)"
    echo "  8) 邮件服务 (25+465+110+995+143+993)"
    echo "  9) 全部常用 (SSH+HTTP+HTTPS+MySQL+Redis)"
    read -rp "请选择 [1-9]: " p_choice

    case "$p_choice" in
        1) service_open "http"; service_open "https" ;;
        2) service_open "mysql" ;;
        3) service_open "redis" ;;
        4) service_open "postgresql" ;;
        5) service_open "ssh" ;;
        6) port_open 21 tcp; port_batch_open "30000 30001 30002 30003 30004 30005" tcp ;;
        7) port_open 53 tcp; port_open 53 udp ;;
        8) port_batch_open "25 465 110 995 143 993" tcp ;;
        9) service_open "ssh"; service_open "http"; service_open "https"; service_open "mysql"; service_open "redis" ;;
        *) warn "无效选择" ;;
    esac
}

# ─── 主入口 ───
check_root

case "${1:-}" in
    open)
        ensure_firewalld
        smart_open "$2" "$3" "$4" "true"
        ;;
    close)
        ensure_firewalld
        smart_close "$2" "$3" "$4"
        ;;
    list)
        ensure_firewalld
        port_list "$2"
        ;;
    status)
        port_status
        ;;
    check)
        ensure_firewalld
        port_check "$2" "${3:-tcp}" "$4"
        ;;
    batch-open)
        ensure_firewalld
        port_batch_open "$2" "${3:-tcp}" "$4"
        ;;
    batch-close)
        ensure_firewalld
        port_batch_close "$2" "${3:-tcp}" "$4"
        ;;
    menu)
        menu
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        # 无参数时进入交互式菜单
        menu
        ;;
esac
