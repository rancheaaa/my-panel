#!/bin/bash
#==========================================================================
#  My-Panel Docker 管理脚本
#  封装 docker compose 常用命令，提供一键式服务管理
#==========================================================================

set -e

# 脚本所在目录（即部署根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 服务列表
SERVICES="mysql redis admin proxy1 proxy2 nginx"

#==========================================
# 获取物理机真实IP地址（生产可用）
# 原理: 优先使用默认路由网卡的源IP，保证每次结果一致；
#       回退方案遍历UP状态的真实网卡（排除docker/虚拟网卡），按名称排序保证确定性。
#==========================================
get_host_ip() {
    local ip=""

    # 方法1: 通过默认路由获取源IP（最可靠）
    # 默认路由使用的网卡就是主物理网卡，不会是 docker/虚拟网卡
    # ip route get 输出示例: "1.1.1.1 via 192.168.1.1 dev eth0 src 192.168.1.100"
    ip=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1); exit}')

    # 方法2: 回退 - 遍历 UP 状态的真实网卡（排除虚拟网卡），按名称排序保证确定性
    if [[ -z "$ip" ]]; then
        for iface in $(ip -o link show up 2>/dev/null | awk -F': ' '{print $2}' | sort); do
            case "$iface" in
                lo|docker*|br-*|veth*|virbr*|tun*|tap*|flannel*|cni*|calico*) continue ;;
            esac
            ip=$(ip -4 addr show "$iface" 2>/dev/null | awk '/inet /{print $2}' | cut -d/ -f1 | head -1)
            [[ -n "$ip" ]] && break
        done
    fi

    # 方法3: 最后回退
    if [[ -z "$ip" ]]; then
        ip=$(hostname -I 2>/dev/null | awk '{print $1}')
    fi

    echo "$ip"
}

HOST_IP=$(get_host_ip)
export HOST_IP
#==========================================
# 打印帮助信息
#==========================================
show_help() {
    echo -e "${CYAN}==========================================================================${NC}"
    echo -e "${CYAN}  My-Panel Docker 管理脚本${NC}"
    echo -e "${CYAN}  封装 docker compose 常用命令${NC}"
    echo -e "${CYAN}==========================================================================${NC}"
    echo ""
    echo -e "${YELLOW}用法:${NC}"
    echo "  $0 <命令> [服务名] [选项]"
    echo ""
    echo -e "${YELLOW}可用命令:${NC}"
    echo ""
    echo -e "  ${GREEN}up       ${NC}  构建镜像并启动所有服务（后台运行）"
    echo -e "                ${NC}  示例: $0 up"
    echo -e "                      $0 up mysql"
    echo ""
    echo -e "  ${GREEN}start    ${NC}  启动已停止的服务（不重新构建）"
    echo -e "                ${NC}  示例: $0 start"
    echo -e "                      $0 start mysql redis"
    echo ""
    echo -e "  ${GREEN}stop     ${NC}  停止服务（保留容器）"
    echo -e "                ${NC}  示例: $0 stop"
    echo -e "                      $0 stop admin proxy1"
    echo ""
    echo -e "  ${GREEN}restart  ${NC}  重启服务"
    echo -e "                ${NC}  示例: $0 restart"
    echo -e "                      $0 restart nginx"
    echo ""
    echo -e "  ${GREEN}down     ${NC}  停止并删除容器（保留数据卷）"
    echo -e "                ${NC}  示例: $0 down"
    echo ""
    echo -e "  ${GREEN}down -v  ${NC}  停止并删除容器及数据卷（慎用！会丢失数据）"
    echo -e "                ${NC}  示例: $0 down -v"
    echo ""
    echo -e "  ${GREEN}ps       ${NC}  查看所有服务状态"
    echo -e "                ${NC}  示例: $0 ps"
    echo ""
    echo -e "  ${GREEN}logs     ${NC}  查看服务日志"
    echo -e "                ${NC}  示例: $0 logs admin"
    echo -e "                      $0 logs proxy1 --tail 100"
    echo -e "                      $0 logs -f admin        (实时跟踪)"
    echo ""
    echo -e "  ${GREEN}build    ${NC}  重新构建镜像（不启动服务）"
    echo -e "                ${NC}  示例: $0 build"
    echo -e "                      $0 build admin"
    echo ""
    echo -e "  ${GREEN}shell    ${NC}  进入容器交互式终端"
    echo -e "                ${NC}  示例: $0 shell admin"
    echo -e "                      $0 shell mysql"
    echo ""
    echo -e "  ${GREEN}health   ${NC}  检查服务健康状态"
    echo -e "                ${NC}  示例: $0 health"
    echo ""
    echo -e "  ${GREEN}ip       ${NC}  显示服务器物理网卡IP地址"
    echo -e "                ${NC}  示例: $0 ip"
    echo ""
    echo -e "  ${GREEN}stats    ${NC}  查看容器资源占用（CPU/内存/网络）"
    echo -e "                ${NC}  示例: $0 stats"
    echo ""
    echo -e "  ${GREEN}config   ${NC}  查看当前 docker-compose.yml 配置"
    echo -e "                ${NC}  示例: $0 config"
    echo ""
    echo -e "  ${GREEN}clean    ${NC}  清理停止的容器和未使用的镜像"
    echo -e "                ${NC}  示例: $0 clean"
    echo ""
    echo -e "  ${GREEN}backup   ${NC}  备份 MySQL 数据库"
    echo -e "                ${NC}  示例: $0 backup"
    echo -e "                      $0 backup /tmp/my-panel-backup.sql"
    echo ""
    echo -e "  ${GREEN}restore  ${NC}  从 SQL 文件恢复 MySQL 数据库"
    echo -e "                ${NC}  示例: $0 restore /tmp/my-panel-backup.sql"
    echo ""
    echo -e "  ${GREEN}help     ${NC}  显示此帮助信息"
    echo ""
    echo -e "${YELLOW}服务列表:${NC}"
    echo "  mysql  redis  admin  proxy1  proxy2  nginx"
    echo ""
    echo -e "${YELLOW}说明:${NC}"
    echo "  - 不指定服务名时，命令作用于所有服务"
    echo "  - 日志默认查看全部服务，可指定服务名查看单个服务日志"
    echo "  - down -v 会删除数据卷（数据库数据将丢失），请谨慎使用"
    echo "  - 日常访问前端控制台: http://localhost:9999"
    echo "  - 直接访问 Admin API:  http://localhost:8888"
    echo -e "  - ${YELLOW}服务器IP: ${HOST_IP:-未获取到}${NC}"
    echo ""
    echo -e "${YELLOW}默认账号:${NC}"
    echo "  admin / admin123    guest / guest123    cq / cq123456"
    echo ""
}

#==========================================
# 检查 docker compose 是否可用
#==========================================
check_compose() {
    if ! docker compose version >/dev/null 2>&1; then
        echo -e "${RED}[错误] docker compose 不可用，请确认已安装 Docker Compose v2${NC}"
        exit 1
    fi
}

#==========================================
# 获取服务名参数（如果有的话）
# 参数: $1 开始的参数作为服务名
#==========================================
get_service_args() {
    local services=""
    for svc in "$@"; do
        # 跳过以 - 开头的选项参数
        [[ "$svc" == -* ]] && continue
        services="$services $svc"
    done
    echo "$services"
}

#==========================================
# 主命令分发
#==========================================
check_compose

COMMAND="${1:-help}"
shift 2>/dev/null || true

case "$COMMAND" in
    #----------------------------------------------------------------------
    # up - 构建镜像并启动服务
    #----------------------------------------------------------------------
    up)
        SERVICES=$(get_service_args "$@")
        echo -e "${CYAN}[构建并启动]${NC} docker compose up -d --build ${SERVICES}"
        docker compose up -d --build $SERVICES
        echo -e "${GREEN}[完成] 服务已启动${NC}"
        docker compose ps
        ;;

    #----------------------------------------------------------------------
    # start - 启动已停止的服务
    #----------------------------------------------------------------------
    start)
        SERVICES=$(get_service_args "$@")
        echo -e "${CYAN}[启动] docker compose start ${SERVICES}"
        docker compose start $SERVICES
        echo -e "${GREEN}[完成] 服务已启动${NC}"
        docker compose ps
        ;;

    #----------------------------------------------------------------------
    # stop - 停止服务
    #----------------------------------------------------------------------
    stop)
        SERVICES=$(get_service_args "$@")
        echo -e "${CYAN}[停止] docker compose stop ${SERVICES}"
        docker compose stop $SERVICES
        echo -e "${GREEN}[完成] 服务已停止${NC}"
        ;;

    #----------------------------------------------------------------------
    # restart - 重启服务
    #----------------------------------------------------------------------
    restart)
        SERVICES=$(get_service_args "$@")
        echo -e "${CYAN}[重启] docker compose restart ${SERVICES}"
        docker compose restart $SERVICES
        echo -e "${GREEN}[完成] 服务已重启${NC}"
        docker compose ps
        ;;

    #----------------------------------------------------------------------
    # down - 停止并删除容器
    #----------------------------------------------------------------------
    down)
        if [[ "$1" == "-v" ]]; then
            echo -e "${RED}[警告] 将删除所有容器及数据卷（数据库数据将丢失！）${NC}"
            read -p "确认继续？(y/N) " confirm
            if [[ "$confirm" == "y" || "$confirm" == "Y" ]]; then
                echo -e "${CYAN}[删除] docker compose down -v${NC}"
                docker compose down -v
                echo -e "${GREEN}[完成] 容器及数据卷已删除${NC}"
            else
                echo -e "${YELLOW}[取消] 操作已取消${NC}"
            fi
        else
            echo -e "${CYAN}[删除] docker compose down${NC}"
            docker compose down
            echo -e "${GREEN}[完成] 容器已删除（数据卷保留）${NC}"
        fi
        ;;

    #----------------------------------------------------------------------
    # ps / status - 查看服务状态
    #----------------------------------------------------------------------
    ps|status)
        docker compose ps "$@"
        ;;

    #----------------------------------------------------------------------
    # logs - 查看日志
    #----------------------------------------------------------------------
    logs)
        if [[ $# -eq 0 ]]; then
            docker compose logs
        else
            docker compose logs "$@"
        fi
        ;;

    #----------------------------------------------------------------------
    # build - 构建镜像
    #----------------------------------------------------------------------
    build)
        SERVICES=$(get_service_args "$@")
        echo -e "${CYAN}[构建] docker compose build ${SERVICES}"
        docker compose build $SERVICES
        echo -e "${GREEN}[完成] 镜像构建完成${NC}"
        ;;

    #----------------------------------------------------------------------
    # shell - 进入容器终端
    #----------------------------------------------------------------------
    shell)
        SVC="${1:-admin}"
        CONTAINER_NAME="my-panel-${SVC}"
        echo -e "${CYAN}[进入] ${CONTAINER_NAME} 交互式终端${NC}"
        if docker exec -it "$CONTAINER_NAME" sh 2>/dev/null; then
            :
        else
            echo -e "${YELLOW}sh 不可用，尝试 bash...${NC}"
            docker exec -it "$CONTAINER_NAME" bash
        fi
        ;;

    #----------------------------------------------------------------------
    # health - 检查健康状态
    #----------------------------------------------------------------------
    health)
        echo -e "${CYAN}==================== 服务健康状态 ====================${NC}"
        for svc in $SERVICES; do
            container="my-panel-${svc}"
            status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null | tr -d '[:space:]')
            if [[ -z "$status" ]]; then
                status="not-found"
            fi
            if [[ "$status" == "healthy" ]]; then
                echo -e "  ${GREEN}●${NC} $svc: ${GREEN}$status${NC}"
            elif [[ "$status" == "unhealthy" ]]; then
                echo -e "  ${RED}●${NC} $svc: ${RED}$status${NC}"
            elif [[ "$status" == "not-found" ]]; then
                running=$(docker inspect --format='{{.State.Running}}' "$container" 2>/dev/null | tr -d '[:space:]')
                if [[ "$running" == "true" ]]; then
                    echo -e "  ${YELLOW}●${NC} $svc: running (无健康检查)"
                else
                    echo -e "  ${RED}●${NC} $svc: 未运行"
                fi
            else
                echo -e "  ${YELLOW}●${NC} $svc: $status"
            fi
        done
        echo -e "${CYAN}=====================================================${NC}"
        ;;

    #----------------------------------------------------------------------
    # ip - 显示服务器物理网卡IP地址
    #----------------------------------------------------------------------
    ip)
        default_iface=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="dev") print $(i+1); exit}')
        echo -e "${CYAN}==================== 服务器IP信息 ====================${NC}"
        if [[ -n "$default_iface" ]]; then
            echo -e "  主网卡:   ${GREEN}${default_iface}${NC}"
        fi
        echo -e "  服务器IP: ${GREEN}${HOST_IP:-未获取到}${NC}"
        echo ""
        echo -e "  ${YELLOW}访问地址:${NC}"
        echo -e "    前端控制台: http://${HOST_IP:-localhost}:9999"
        echo -e "    Admin API:  http://${HOST_IP:-localhost}:8888"
        echo -e "${CYAN}=====================================================${NC}"
        ;;

    #----------------------------------------------------------------------
    # stats - 查看资源占用
    #----------------------------------------------------------------------
    stats)
        echo -e "${CYAN}[资源占用] 按 Ctrl+C 退出${NC}"
        docker stats $(for svc in $SERVICES; do echo -n "my-panel-${svc} "; done)
        ;;

    #----------------------------------------------------------------------
    # config - 查看配置
    #----------------------------------------------------------------------
    config)
        docker compose config
        ;;

    #----------------------------------------------------------------------
    # clean - 清理未使用的容器和镜像
    #----------------------------------------------------------------------
    clean)
        echo -e "${CYAN}[清理] 停止的容器和未使用的镜像${NC}"
        read -p "确认清理？(y/N) " confirm
        if [[ "$confirm" == "y" || "$confirm" == "Y" ]]; then
            docker container prune -f
            docker image prune -f
            echo -e "${GREEN}[完成] 清理完成${NC}"
        else
            echo -e "${YELLOW}[取消] 操作已取消${NC}"
        fi
        ;;

    #----------------------------------------------------------------------
    # backup - 备份 MySQL
    #----------------------------------------------------------------------
    backup)
        BACKUP_FILE="${1:-./backup/my-panel-$(date +%Y%m%d_%H%M%S).sql}"
        BACKUP_DIR=$(dirname "$BACKUP_FILE")
        mkdir -p "$BACKUP_DIR"
        echo -e "${CYAN}[备份] MySQL 数据库 -> ${BACKUP_FILE}${NC}"
        docker exec my-panel-mysql mysqldump -uroot -p123456 --single-transaction --routines --triggers my-panel > "$BACKUP_FILE"
        FILESIZE=$(du -h "$BACKUP_FILE" | cut -f1)
        echo -e "${GREEN}[完成] 备份完成: ${BACKUP_FILE} (${FILESIZE})${NC}"
        ;;

    #----------------------------------------------------------------------
    # restore - 恢复 MySQL
    #----------------------------------------------------------------------
    restore)
        RESTORE_FILE="$1"
        if [[ -z "$RESTORE_FILE" ]]; then
            echo -e "${RED}[错误] 请指定 SQL 备份文件路径${NC}"
            echo -e "  用法: $0 restore <sql文件路径>"
            exit 1
        fi
        if [[ ! -f "$RESTORE_FILE" ]]; then
            echo -e "${RED}[错误] 文件不存在: ${RESTORE_FILE}${NC}"
            exit 1
        fi
        echo -e "${RED}[警告] 将恢复数据库，当前数据将被覆盖！${NC}"
        read -p "确认恢复？(y/N) " confirm
        if [[ "$confirm" == "y" || "$confirm" == "Y" ]]; then
            echo -e "${CYAN}[恢复] ${RESTORE_FILE} -> MySQL${NC}"
            docker exec -i my-panel-mysql mysql -uroot -p123456 my-panel < "$RESTORE_FILE"
            echo -e "${GREEN}[完成] 数据库恢复完成${NC}"
        else
            echo -e "${YELLOW}[取消] 操作已取消${NC}"
        fi
        ;;

    #----------------------------------------------------------------------
    # help - 显示帮助
    #----------------------------------------------------------------------
    help|--help|-h)
        show_help
        ;;

    #----------------------------------------------------------------------
    # 未知命令
    #----------------------------------------------------------------------
    *)
        echo -e "${RED}[错误] 未知命令: ${COMMAND}${NC}"
        echo -e "${YELLOW}使用 '$0 help' 查看可用命令${NC}"
        exit 1
        ;;
esac
