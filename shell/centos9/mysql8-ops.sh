#!/bin/bash

# =============================================
#  MySQL 8 交互式运维脚本 (CentOS 9 / RHEL 9)
#  功能: 用户管理 / 数据库管理 / 权限管理 / 运维监控 / 备份恢复
# chmod +x mysql8-ops.sh
# sudo ./mysql8-ops.sh
# =============================================

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-MyPanel@2026}"
MYSQL_CONF_DIR="/etc/my.cnf.d"
MYSQL_LOG_DIR="/var/log/mysql"
MYSQL_BACKUP_DIR="${MYSQL_BACKUP_DIR:-/backup/mysql}"

# ─── 颜色定义 ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
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

# ─── 检测 MySQL 是否运行 ───
is_mysql_running() {
    systemctl is-active --quiet mysqld 2>/dev/null
}

# ─── 获取端口 ───
get_mysql_port() {
    local port
    if [ -f "${MYSQL_CONF_DIR}/my-panel-port.cnf" ]; then
        port=$(grep -P '^\s*port\s*=' "${MYSQL_CONF_DIR}/my-panel-port.cnf" | head -1 | awk -F= '{print $2}' | tr -d ' ')
    fi
    if [ -z "$port" ] && is_mysql_running; then
        port=$(ss -tlnp 2>/dev/null | grep mysqld | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
    fi
    echo "${port:-3306}"
}

# ─── 执行 SQL ───
run_sql() {
    local port
    port=$(get_mysql_port)
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -h 127.0.0.1 -P "$port" -e "$@" 2>/dev/null
}

# ─── 执行 SQL（静默，返回结果） ───
run_sql_silent() {
    local port
    port=$(get_mysql_port)
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -h 127.0.0.1 -P "$port" -s -N -e "$@" 2>/dev/null
}

# ─── 前置检查 ───
pre_check() {
    check_root
    if ! is_mysql_running; then
        error "MySQL 未运行，请先启动: systemctl start mysqld"
        exit 1
    fi
    # 测试连接
    if ! run_sql "SELECT 1;" &>/dev/null; then
        error "MySQL 连接失败，请检查密码是否正确 (当前: ${MYSQL_ROOT_PASSWORD})"
        error "可通过环境变量 MYSQL_ROOT_PASSWORD 指定密码"
        exit 1
    fi
}

# ─── 读取非空输入 ───
read_nonempty() {
    local prompt="$1"
    local var_name="$2"
    local value
    while true; do
        read -rp "$prompt" value
        if [ -n "$value" ]; then
            eval "$var_name='$value'"
            return 0
        fi
        warn "输入不能为空，请重新输入"
    done
}

# ─── 读取确认 ───
read_confirm() {
    local prompt="$1"
    local answer
    read -rp "$prompt [y/N]: " answer
    [[ "$answer" =~ ^[Yy]$ ]]
}

# ─── 暂停 ───
pause() {
    echo ""
    read -rn 1 -s -p "按任意键继续..."
    echo ""
}

# =============================================
#  用户管理
# =============================================

# ─── 修改 root 密码 ───
change_root_password() {
    title "修改 root 密码"
    local new_pass new_pass2
    read -sp "请输入新密码: " new_pass
    echo ""
    read -sp "再次输入新密码: " new_pass2
    echo ""

    if [ "$new_pass" != "$new_pass2" ]; then
        error "两次密码不一致"
        return 1
    fi

    if [ -z "$new_pass" ]; then
        error "密码不能为空"
        return 1
    fi

    if run_sql "ALTER USER 'root'@'localhost' IDENTIFIED BY '${new_pass}'; FLUSH PRIVILEGES;"; then
        # 同步修改远程 root
        run_sql "ALTER USER 'root'@'%' IDENTIFIED BY '${new_pass}';" 2>/dev/null || true
        MYSQL_ROOT_PASSWORD="$new_pass"
        info "root 密码修改成功"
    else
        error "密码修改失败，可能不满足密码策略要求"
        info "可先执行: SET GLOBAL validate_password.policy=LOW; SET GLOBAL validate_password.length=6;"
    fi
}

# ─── 创建用户 ───
create_user() {
    title "创建新用户"
    local username password password2 host

    read_nonempty "用户名: " username
    read -sp "密码: " password
    echo ""
    read -sp "确认密码: " password2
    echo ""

    if [ "$password" != "$password2" ]; then
        error "两次密码不一致"
        return 1
    fi

    echo ""
    echo "允许登录的主机:"
    echo "  1) localhost (仅本机)"
    echo "  2) % (任意IP)"
    echo "  3) 指定IP/网段"
    read -rp "请选择 [1-3]: " host_choice

    case "$host_choice" in
        1) host="localhost" ;;
        2) host="%" ;;
        3) read_nonempty "请输入IP或网段 (如 192.168.1.%): " host ;;
        *) host="%" ;;
    esac

    # 选择认证插件
    echo ""
    echo "认证插件:"
    echo "  1) mysql_native_password (兼容性好)"
    echo "  2) caching_sha2_password (MySQL 8 默认，更安全)"
    read -rp "请选择 [1-2, 默认1]: " plugin_choice

    local plugin="mysql_native_password"
    [ "$plugin_choice" = "2" ] && plugin="caching_sha2_password"

    if run_sql "CREATE USER '${username}'@'${host}' IDENTIFIED WITH ${plugin} BY '${password}';"; then
        info "用户 '${username}'@'${host}' 创建成功 (认证: ${plugin})"
    else
        error "创建用户失败"
    fi
}

# ─── 删除用户 ───
delete_user() {
    title "删除用户"
    list_users
    echo ""

    local username host
    read_nonempty "要删除的用户名: " username
    read_nonempty "该用户的主机 (如 localhost 或 %): " host

    if read_confirm "确认删除用户 '${username}'@'${host}'？"; then
        if run_sql "DROP USER '${username}'@'${host}';"; then
            info "用户 '${username}'@'${host}' 已删除"
        else
            error "删除用户失败，请确认用户存在"
        fi
    else
        info "已取消"
    fi
}

# ─── 修改用户密码 ───
change_user_password() {
    title "修改用户密码"
    local username host new_pass new_pass2

    read_nonempty "用户名: " username
    read_nonempty "主机 (如 localhost 或 %): " host
    read -sp "新密码: " new_pass
    echo ""
    read -sp "确认新密码: " new_pass2
    echo ""

    if [ "$new_pass" != "$new_pass2" ]; then
        error "两次密码不一致"
        return 1
    fi

    if run_sql "ALTER USER '${username}'@'${host}' IDENTIFIED BY '${new_pass}'; FLUSH PRIVILEGES;"; then
        info "用户 '${username}'@'${host}' 密码修改成功"
    else
        error "密码修改失败"
    fi
}

# ─── 设置用户允许任意IP访问 ───
grant_remote_access() {
    title "设置用户允许任意IP访问"
    local username password

    read_nonempty "用户名: " username

    # 检查用户是否已有 % 主机
    local existing
    existing=$(run_sql_silent "SELECT COUNT(*) FROM mysql.user WHERE User='${username}' AND Host='%';")
    if [ "$existing" -gt 0 ]; then
        info "用户 '${username}'@'%' 已存在"
        if read_confirm "是否重置其密码？"; then
            read -sp "新密码: " password
            echo ""
            run_sql "ALTER USER '${username}'@'%' IDENTIFIED WITH mysql_native_password BY '${password}'; FLUSH PRIVILEGES;" && \
                info "密码已重置" || error "密码重置失败"
        fi
    else
        # 检查 localhost 用户
        local local_exists
        local_exists=$(run_sql_silent "SELECT COUNT(*) FROM mysql.user WHERE User='${username}' AND Host='localhost';")
        if [ "$local_exists" -gt 0 ]; then
            info "发现 '${username}'@'localhost'，将创建同名的 '%' 用户"
        fi
        read -sp "密码: " password
        echo ""
        if run_sql "CREATE USER IF NOT EXISTS '${username}'@'%' IDENTIFIED WITH mysql_native_password BY '${password}';"; then
            info "用户 '${username}'@'%' 创建成功"
        else
            error "创建用户失败"
            return 1
        fi
    fi

    # 授权
    echo ""
    echo "授权范围:"
    echo "  1) 所有数据库所有权限"
    echo "  2) 指定数据库所有权限"
    echo "  3) 指定数据库指定权限"
    echo "  4) 不授权 (仅创建/修改用户)"
    read -rp "请选择 [1-4]: " grant_choice

    case "$grant_choice" in
        1)
            run_sql "GRANT ALL PRIVILEGES ON *.* TO '${username}'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"
            info "已授予所有权限"
            ;;
        2)
            local db_name
            read_nonempty "数据库名 (可用 * 通配): " db_name
            run_sql "GRANT ALL PRIVILEGES ON \`${db_name}\`.* TO '${username}'@'%'; FLUSH PRIVILEGES;"
            info "已授予数据库 ${db_name} 的所有权限"
            ;;
        3)
            local db_name privileges
            read_nonempty "数据库名: " db_name
            echo "常用权限: SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, EXECUTE"
            read_nonempty "权限列表 (逗号分隔): " privileges
            run_sql "GRANT ${privileges} ON \`${db_name}\`.* TO '${username}'@'%'; FLUSH PRIVILEGES;"
            info "已授予权限: ${privileges} on ${db_name}"
            ;;
        4)
            info "未授权，可稍后通过权限管理菜单操作"
            ;;
    esac
}

# ─── 列出所有用户 ───
list_users() {
    title "所有 MySQL 用户"
    run_sql "SELECT User, Host, plugin FROM mysql.user ORDER BY User, Host;" | column -t
}

# ─── 查看用户权限 ───
show_user_grants() {
    title "查看用户权限"
    local username host
    read_nonempty "用户名: " username
    read_nonempty "主机 (如 localhost 或 %): " host

    echo ""
    run_sql "SHOW GRANTS FOR '${username}'@'${host}';"
}

# ─── 用户管理菜单 ───
menu_user_management() {
    while true; do
        title "用户管理"
        echo "  1) 创建新用户"
        echo "  2) 删除用户"
        echo "  3) 修改用户密码"
        echo "  4) 修改 root 密码"
        echo "  5) 设置用户允许任意IP访问"
        echo "  6) 列出所有用户"
        echo "  7) 查看用户权限"
        echo "  0) 返回上级"
        echo ""
        read -rp "请选择 [0-7]: " choice

        case "$choice" in
            1) create_user ;;
            2) delete_user ;;
            3) change_user_password ;;
            4) change_root_password ;;
            5) grant_remote_access ;;
            6) list_users ;;
            7) show_user_grants ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

# =============================================
#  权限管理
# =============================================

# ─── 授予权限 ───
grant_privileges() {
    title "授予权限"
    local username host db_name privileges

    read_nonempty "用户名: " username
    read_nonempty "主机 (如 localhost 或 %): " host

    echo ""
    echo "授权范围:"
    echo "  1) 所有数据库 (*.*)"
    echo "  2) 指定数据库"
    read -rp "请选择 [1-2]: " scope_choice

    local grant_target
    case "$scope_choice" in
        1)
            grant_target="*.*"
            ;;
        2)
            read_nonempty "数据库名: " db_name
            grant_target="\`${db_name}\`.*"
            ;;
        *)
            warn "无效选择"; return 1 ;;
    esac

    echo ""
    echo "常用权限:"
    echo "  ALL PRIVILEGES  - 所有权限"
    echo "  SELECT          - 查询"
    echo "  INSERT,UPDATE,DELETE - 增删改"
    echo "  CREATE,ALTER,DROP    - DDL"
    echo "  INDEX           - 索引"
    echo "  EXECUTE         - 执行存储过程"
    echo "  REPLICATION SLAVE   - 复制从库"
    echo ""
    read_nonempty "权限列表 (逗号分隔): " privileges

    local with_grant=""
    if read_confirm "是否允许该用户授权给其他用户 (WITH GRANT OPTION)？"; then
        with_grant=" WITH GRANT OPTION"
    fi

    if run_sql "GRANT ${privileges} ON ${grant_target} TO '${username}'@'${host}'${with_grant}; FLUSH PRIVILEGES;"; then
        info "授权成功: ${privileges} ON ${grant_target} -> '${username}'@'${host}'"
    else
        error "授权失败"
    fi
}

# ─── 撤销权限 ───
revoke_privileges() {
    title "撤销权限"
    local username host db_name privileges

    read_nonempty "用户名: " username
    read_nonempty "主机 (如 localhost 或 %): " host

    echo ""
    echo "当前权限:"
    run_sql "SHOW GRANTS FOR '${username}'@'${host}';"
    echo ""

    echo "撤销范围:"
    echo "  1) 所有数据库 (*.*)"
    echo "  2) 指定数据库"
    read -rp "请选择 [1-2]: " scope_choice

    local revoke_target
    case "$scope_choice" in
        1) revoke_target="*.*" ;;
        2)
            read_nonempty "数据库名: " db_name
            revoke_target="\`${db_name}\`.*"
            ;;
        *) warn "无效选择"; return 1 ;;
    esac

    echo ""
    read_nonempty "要撤销的权限 (输入 ALL PRIVILEGES 撤销全部): " privileges

    if run_sql "REVOKE ${privileges} ON ${revoke_target} FROM '${username}'@'${host}'; FLUSH PRIVILEGES;"; then
        info "权限已撤销"
    else
        error "撤销失败"
    fi
}

# ─── 权限管理菜单 ───
menu_privilege_management() {
    while true; do
        title "权限管理"
        echo "  1) 授予权限"
        echo "  2) 撤销权限"
        echo "  3) 查看用户权限"
        echo "  4) 刷新权限 (FLUSH PRIVILEGES)"
        echo "  0) 返回上级"
        echo ""
        read -rp "请选择 [0-4]: " choice

        case "$choice" in
            1) grant_privileges ;;
            2) revoke_privileges ;;
            3)
                local u h
                read_nonempty "用户名: " u
                read_nonempty "主机: " h
                run_sql "SHOW GRANTS FOR '${u}'@'${h}';"
                ;;
            4)
                run_sql "FLUSH PRIVILEGES;"
                info "权限已刷新"
                ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

# =============================================
#  数据库管理
# =============================================

# ─── 创建数据库 ───
create_database() {
    title "创建数据库"
    local db_name charset collate

    read_nonempty "数据库名: " db_name

    echo ""
    echo "字符集:"
    echo "  1) utf8mb4 (推荐)"
    echo "  2) utf8"
    echo "  3) latin1"
    echo "  4) gbk"
    read -rp "请选择 [1-4, 默认1]: " cs_choice

    case "$cs_choice" in
        2) charset="utf8"; collate="utf8_general_ci" ;;
        3) charset="latin1"; collate="latin1_swedish_ci" ;;
        4) charset="gbk"; collate="gbk_chinese_ci" ;;
        *) charset="utf8mb4"; collate="utf8mb4_unicode_ci" ;;
    esac

    if run_sql "CREATE DATABASE \`${db_name}\` CHARACTER SET ${charset} COLLATE ${collate};"; then
        info "数据库 '${db_name}' 创建成功 (字符集: ${charset}, 排序: ${collate})"

        if read_confirm "是否立即为用户授权该数据库？"; then
            local username
            read_nonempty "用户名: " username
            local host
            read -rp "主机 [默认 %]: " host
            host="${host:-%}"
            run_sql "GRANT ALL PRIVILEGES ON \`${db_name}\`.* TO '${username}'@'${host}'; FLUSH PRIVILEGES;"
            info "已授权 '${username}'@'${host}' 对 ${db_name} 的所有权限"
        fi
    else
        error "创建数据库失败"
    fi
}

# ─── 删除数据库 ───
delete_database() {
    title "删除数据库"
    list_databases
    echo ""

    local db_name
    read_nonempty "要删除的数据库名: " db_name

    warn "警告: 删除数据库将丢失所有数据！"
    read -rp "请输入数据库名确认删除: " confirm
    if [ "$confirm" != "$db_name" ]; then
        info "确认不匹配，已取消"
        return 0
    fi

    if run_sql "DROP DATABASE \`${db_name}\`;"; then
        info "数据库 '${db_name}' 已删除"
    else
        error "删除失败"
    fi
}

# ─── 列出数据库 ───
list_databases() {
    title "数据库列表"
    run_sql "SELECT table_schema AS '数据库', ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS '大小(MB)', COUNT(*) AS '表数量' FROM information_schema.tables GROUP BY table_schema ORDER BY SUM(data_length + index_length) DESC;" | column -t
}

# ─── 查看数据库表 ───
show_tables() {
    title "查看数据库表"
    local db_name
    read_nonempty "数据库名: " db_name

    echo ""
    run_sql "SELECT TABLE_NAME AS '表名', TABLE_ROWS AS '行数', ROUND(DATA_LENGTH/1024/1024, 2) AS '数据(MB)', ROUND(INDEX_LENGTH/1024/1024, 2) AS '索引(MB)', ENGINE AS '引擎', TABLE_COLLATION AS '排序规则' FROM information_schema.tables WHERE TABLE_SCHEMA='${db_name}' ORDER BY DATA_LENGTH DESC;" | column -t
}

# ─── 数据库管理菜单 ───
menu_database_management() {
    while true; do
        title "数据库管理"
        echo "  1) 创建数据库"
        echo "  2) 删除数据库"
        echo "  3) 列出数据库及大小"
        echo "  4) 查看数据库表"
        echo "  0) 返回上级"
        echo ""
        read -rp "请选择 [0-4]: " choice

        case "$choice" in
            1) create_database ;;
            2) delete_database ;;
            3) list_databases ;;
            4) show_tables ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

# =============================================
#  备份与恢复
# =============================================

# ─── 备份数据库 ───
backup_database() {
    title "备份数据库"
    mkdir -p "$MYSQL_BACKUP_DIR"

    echo "备份范围:"
    echo "  1) 备份指定数据库"
    echo "  2) 备份所有数据库"
    read -rp "请选择 [1-2]: " bk_choice

    local port
    port=$(get_mysql_port)
    local timestamp
    timestamp=$(date +%Y%m%d_%H%M%S)

    case "$bk_choice" in
        1)
            local db_name
            read_nonempty "数据库名: " db_name
            local bk_file="${MYSQL_BACKUP_DIR}/${db_name}_${timestamp}.sql.gz"
            info "正在备份 ${db_name} -> ${bk_file} ..."
            if mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" -h 127.0.0.1 -P "$port" --single-transaction --routines --triggers --events "${db_name}" 2>/dev/null | gzip > "$bk_file"; then
                local size
                size=$(du -sh "$bk_file" | awk '{print $1}')
                info "备份成功: ${bk_file} (${size})"
            else
                error "备份失败"
                rm -f "$bk_file"
            fi
            ;;
        2)
            local bk_file="${MYSQL_BACKUP_DIR}/all_databases_${timestamp}.sql.gz"
            info "正在备份所有数据库 -> ${bk_file} ..."
            if mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" -h 127.0.0.1 -P "$port" --single-transaction --routines --triggers --events --all-databases 2>/dev/null | gzip > "$bk_file"; then
                local size
                size=$(du -sh "$bk_file" | awk '{print $1}')
                info "备份成功: ${bk_file} (${size})"
            else
                error "备份失败"
                rm -f "$bk_file"
            fi
            ;;
        *)
            warn "无效选择"; return ;;
    esac
}

# ─── 恢复数据库 ───
restore_database() {
    title "恢复数据库"

    if [ ! -d "$MYSQL_BACKUP_DIR" ] || [ -z "$(ls -A "$MYSQL_BACKUP_DIR" 2>/dev/null)" ]; then
        warn "备份目录为空: ${MYSQL_BACKUP_DIR}"
        return
    fi

    echo "可用备份文件:"
    local files=()
    local i=1
    for f in "${MYSQL_BACKUP_DIR}"/*.sql.gz; do
        [ -f "$f" ] || continue
        local size
        size=$(du -sh "$f" | awk '{print $1}')
        local mtime
        mtime=$(stat -c '%y' "$f" 2>/dev/null | cut -d'.' -f1)
        echo "  ${i}) $(basename "$f")  (${size}, ${mtime})"
        files+=("$f")
        i=$((i + 1))
    done

    if [ ${#files[@]} -eq 0 ]; then
        warn "未找到备份文件"
        return
    fi

    echo ""
    read -rp "选择备份文件编号 [1-${#files[@]}]: " file_choice
    if ! [[ "$file_choice" =~ ^[0-9]+$ ]] || [ "$file_choice" -lt 1 ] || [ "$file_choice" -gt ${#files[@]} ]; then
        error "无效选择"
        return
    fi

    local selected_file="${files[$((file_choice - 1))]}"
    local db_name
    read_nonempty "恢复到数据库名: " db_name

    warn "注意: 恢复操作将覆盖目标数据库中的同名表！"
    if ! read_confirm "确认恢复？"; then
        info "已取消"
        return
    fi

    local port
    port=$(get_mysql_port)

    # 确保数据库存在
    run_sql "CREATE DATABASE IF NOT EXISTS \`${db_name}\`;"

    info "正在恢复 ${selected_file} -> ${db_name} ..."
    if gunzip -c "$selected_file" | mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -h 127.0.0.1 -P "$port" "$db_name" 2>/dev/null; then
        info "恢复成功"
    else
        error "恢复失败，请检查备份文件和目标数据库"
    fi
}

# ─── 列出备份 ───
list_backups() {
    title "备份文件列表"
    if [ ! -d "$MYSQL_BACKUP_DIR" ] || [ -z "$(ls -A "$MYSQL_BACKUP_DIR" 2>/dev/null)" ]; then
        warn "备份目录为空: ${MYSQL_BACKUP_DIR}"
        return
    fi
    echo "目录: ${MYSQL_BACKUP_DIR}"
    echo ""
    ls -lh "${MYSQL_BACKUP_DIR}"/*.sql.gz 2>/dev/null || warn "无备份文件"
}

# ─── 备份恢复菜单 ───
menu_backup_restore() {
    while true; do
        title "备份与恢复"
        echo "  1) 备份数据库"
        echo "  2) 恢复数据库"
        echo "  3) 列出备份文件"
        echo "  0) 返回上级"
        echo ""
        read -rp "请选择 [0-3]: " choice

        case "$choice" in
            1) backup_database ;;
            2) restore_database ;;
            3) list_backups ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

# =============================================
#  运维监控
# =============================================

# ─── 查看连接 ───
show_processlist() {
    title "当前连接"
    echo "  1) 简要列表"
    echo "  2) 完整列表 (含 SQL)"
    read -rp "请选择 [1-2]: " pl_choice

    case "$pl_choice" in
        2) run_sql "SHOW FULL PROCESSLIST;" | column -t ;;
        *) run_sql "SHOW PROCESSLIST;" | column -t ;;
    esac

    echo ""
    local conn_count
    conn_count=$(run_sql_silent "SELECT COUNT(*) FROM information_schema.processlist;")
    info "当前连接数: ${conn_count}"
}

# ─── 杀掉连接 ───
kill_connection() {
    title "终止连接"
    show_processlist
    echo ""

    local pid
    read_nonempty "要终止的连接 ID: " pid

    if read_confirm "确认终止连接 ${pid}？"; then
        if run_sql "KILL ${pid};"; then
            info "连接 ${pid} 已终止"
        else
            error "终止失败，可能连接已不存在"
        fi
    fi
}

# ─── 查看变量 ───
show_variables() {
    title "MySQL 变量"
    local keyword
    read -rp "搜索关键词 (留空显示全部): " keyword

    if [ -n "$keyword" ]; then
        run_sql "SHOW VARIABLES LIKE '%${keyword}%';" | column -t
    else
        echo "  1) 字符集相关"
        echo "  2) 连接相关"
        echo "  3) InnoDB 相关"
        echo "  4) 日志相关"
        echo "  5) 全部"
        read -rp "请选择 [1-5]: " var_choice

        case "$var_choice" in
            1) run_sql "SHOW VARIABLES LIKE 'character%'; SHOW VARIABLES LIKE 'collation%';" | column -t ;;
            2) run_sql "SHOW VARIABLES LIKE 'max_connections'; SHOW VARIABLES LIKE 'wait_timeout'; SHOW VARIABLES LIKE 'interactive_timeout'; SHOW VARIABLES LIKE 'thread_cache_size';" | column -t ;;
            3) run_sql "SHOW VARIABLES LIKE 'innodb%';" | column -t ;;
            4) run_sql "SHOW VARIABLES LIKE '%log%';" | column -t ;;
            5) run_sql "SHOW VARIABLES;" | column -t ;;
        esac
    fi
}

# ─── 查看状态 ───
show_server_status() {
    title "MySQL 状态"
    echo "  1) 连接状态"
    echo "  2) InnoDB 状态"
    echo "  3) 主从复制状态"
    echo "  4) 慢查询统计"
    echo "  5) QPS / TPS 统计"
    read -rp "请选择 [1-5]: " st_choice

    case "$st_choice" in
        1)
            run_sql "SHOW STATUS LIKE 'Threads%'; SHOW STATUS LIKE 'Connections'; SHOW STATUS LIKE 'Max_used_connections'; SHOW STATUS LIKE 'Aborted%';" | column -t
            ;;
        2)
            run_sql "SHOW ENGINE INNODB STATUS\G" 2>/dev/null | head -80
            ;;
        3)
            run_sql "SHOW MASTER STATUS\G" 2>/dev/null
            echo ""
            run_sql "SHOW SLAVE STATUS\G" 2>/dev/null || info "未配置从库"
            ;;
        4)
            run_sql "SHOW VARIABLES LIKE 'slow_query%'; SHOW VARIABLES LIKE 'long_query_time'; SHOW STATUS LIKE 'Slow_queries';" | column -t
            ;;
        5)
            local q1 q2 t1 t2
            q1=$(run_sql_silent "SHOW STATUS LIKE 'Queries';" | awk '{print $2}')
            t1=$(run_sql_silent "SHOW STATUS LIKE 'Com_insert' OR STATUS LIKE 'Com_update' OR STATUS LIKE 'Com_delete';" 2>/dev/null | awk '{sum+=$2} END{print sum}')
            sleep 1
            q2=$(run_sql_silent "SHOW STATUS LIKE 'Queries';" | awk '{print $2}')
            t2=$(run_sql_silent "SHOW STATUS LIKE 'Com_insert' OR STATUS LIKE 'Com_update' OR STATUS LIKE 'Com_delete';" 2>/dev/null | awk '{sum+=$2} END{print sum}')
            local qps=$((q2 - q1))
            local tps=$((t2 - t1))
            info "QPS (每秒查询数): ${qps}"
            info "TPS (每秒事务数): ${tps}"
            ;;
    esac
}

# ─── 查看慢查询 ───
show_slow_queries() {
    title "慢查询日志"
    local slow_log
    slow_log=$(run_sql_silent "SHOW VARIABLES LIKE 'slow_query_log_file';" | awk '{print $2}')

    if [ -z "$slow_log" ] || [ ! -f "$slow_log" ]; then
        slow_log="${MYSQL_LOG_DIR}/slow.log"
    fi

    if [ ! -f "$slow_log" ]; then
        warn "慢查询日志文件不存在: ${slow_log}"
        return
    fi

    echo "日志文件: ${slow_log}"
    echo ""
    echo "  1) 最近 20 条慢查询"
    echo "  2) 统计最慢的 Top 10"
    echo "  3) 统计最频繁的 Top 10"
    read -rp "请选择 [1-3]: " sq_choice

    case "$sq_choice" in
        1) tail -200 "$slow_log" | grep -A5 "Query_time" | head -100 ;;
        2)
            if command -v mysqldumpslow &>/dev/null; then
                mysqldumpslow -s t -t 10 "$slow_log"
            else
                warn "mysqldumpslow 不可用，显示原始日志末尾"
                tail -50 "$slow_log"
            fi
            ;;
        3)
            if command -v mysqldumpslow &>/dev/null; then
                mysqldumpslow -s c -t 10 "$slow_log"
            else
                warn "mysqldumpslow 不可用"
                tail -50 "$slow_log"
            fi
            ;;
    esac
}

# ─── 查看错误日志 ───
show_error_log() {
    title "错误日志"
    local error_log
    error_log=$(run_sql_silent "SHOW VARIABLES LIKE 'log_error';" | awk '{print $2}')

    if [ -z "$error_log" ] || [ ! -f "$error_log" ]; then
        error_log="${MYSQL_LOG_DIR}/error.log"
    fi

    if [ ! -f "$error_log" ]; then
        warn "错误日志文件不存在: ${error_log}"
        return
    fi

    echo "日志文件: ${error_log}"
    echo ""
    echo "  1) 最近 50 行"
    echo "  2) 最近 200 行"
    echo "  3) 搜索关键词"
    read -rp "请选择 [1-3]: " el_choice

    case "$el_choice" in
        1) tail -50 "$error_log" ;;
        2) tail -200 "$error_log" ;;
        3)
            local keyword
            read -rp "搜索关键词: " keyword
            grep -i "$keyword" "$error_log" | tail -50
            ;;
    esac
}

# ─── 运维监控菜单 ───
menu_monitor() {
    while true; do
        title "运维监控"
        echo "  1) 查看当前连接"
        echo "  2) 终止连接"
        echo "  3) 查看变量"
        echo "  4) 服务器状态"
        echo "  5) 慢查询日志"
        echo "  6) 错误日志"
        echo "  0) 返回上级"
        echo ""
        read -rp "请选择 [0-6]: " choice

        case "$choice" in
            1) show_processlist ;;
            2) kill_connection ;;
            3) show_variables ;;
            4) show_server_status ;;
            5) show_slow_queries ;;
            6) show_error_log ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

# =============================================
#  配置管理
# =============================================

# ─── 修改端口 ───
change_port() {
    title "修改 MySQL 端口"
    local current_port
    current_port=$(get_mysql_port)
    info "当前端口: ${current_port}"

    local new_port
    read_nonempty "新端口: " new_port

    if ! [[ "$new_port" =~ ^[0-9]+$ ]] || [ "$new_port" -lt 1 ] || [ "$new_port" -gt 65535 ]; then
        error "无效端口号"
        return 1
    fi

    if [ "$current_port" = "$new_port" ]; then
        info "端口未变"
        return 0
    fi

    # 检查端口占用
    if ss -tlnp 2>/dev/null | grep -q ":${new_port} "; then
        error "端口 ${new_port} 已被占用"
        return 1
    fi

    # 防火墙
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        firewall-cmd --permanent --add-port="${new_port}/tcp" 2>/dev/null || true
        firewall-cmd --permanent --remove-port="${current_port}/tcp" 2>/dev/null || true
        firewall-cmd --reload 2>/dev/null || true
        info "防火墙规则已更新"
    fi

    # 写入配置
    local port_conf="${MYSQL_CONF_DIR}/my-panel-port.cnf"
    cat > "$port_conf" <<EOF
[mysqld]
port=${new_port}

[client]
port=${new_port}
EOF
    chown mysql:mysql "$port_conf"
    info "端口配置已写入: ${port_conf}"

    if read_confirm "是否立即重启 MySQL 使新端口生效？"; then
        systemctl restart mysqld
        sleep 3
        local verify_port
        verify_port=$(ss -tlnp 2>/dev/null | grep mysqld | awk '{print $4}' | grep -oP ':\d+$' | head -1 | tr -d ':')
        if [ "$verify_port" = "$new_port" ]; then
            info "端口修改成功: ${current_port} -> ${new_port}"
        else
            warn "端口验证: ${verify_port:-未检测到}，请检查 MySQL 状态"
        fi
    else
        info "下次启动 MySQL 时将使用新端口 ${new_port}"
    fi
}

# ─── 修改最大连接数 ───
change_max_connections() {
    title "修改最大连接数"
    local current
    current=$(run_sql_silent "SHOW VARIABLES LIKE 'max_connections';" | awk '{print $2}')
    info "当前最大连接数: ${current}"

    local new_val
    read_nonempty "新的最大连接数: " new_val

    # 运行时修改
    if run_sql "SET GLOBAL max_connections=${new_val};"; then
        info "运行时已修改为: ${new_val}"
    else
        error "修改失败"
        return 1
    fi

    # 持久化到配置文件
    local conf_file="${MYSQL_CONF_DIR}/my-panel.cnf"
    if grep -q '^max_connections=' "$conf_file" 2>/dev/null; then
        sed -i "s/^max_connections=.*/max_connections=${new_val}/" "$conf_file"
    else
        echo "max_connections=${new_val}" >> "$conf_file"
    fi
    info "配置文件已更新: ${conf_file}"
}

# ─── 开关慢查询日志 ───
toggle_slow_query() {
    title "慢查询日志开关"
    local current
    current=$(run_sql_silent "SHOW VARIABLES LIKE 'slow_query_log';" | awk '{print $2}')
    info "当前状态: ${current}"

    if [ "$current" = "ON" ]; then
        if read_confirm "是否关闭慢查询日志？"; then
            run_sql "SET GLOBAL slow_query_log=OFF;"
            info "慢查询日志已关闭"
        fi
    else
        if read_confirm "是否开启慢查询日志？"; then
            local long_time
            read -rp "慢查询阈值(秒) [默认2]: " long_time
            long_time="${long_time:-2}"
            run_sql "SET GLOBAL slow_query_log=ON; SET GLOBAL long_query_time=${long_time};"
            info "慢查询日志已开启 (阈值: ${long_time}秒)"
        fi
    fi
}

# ─── 配置管理菜单 ───
menu_config() {
    while true; do
        title "配置管理"
        echo "  1) 修改监听端口"
        echo "  2) 修改最大连接数"
        echo "  3) 开关慢查询日志"
        echo "  0) 返回上级"
        echo ""
        read -rp "请选择 [0-3]: " choice

        case "$choice" in
            1) change_port ;;
            2) change_max_connections ;;
            3) toggle_slow_query ;;
            0) return ;;
            *) warn "无效选择" ;;
        esac
        pause
    done
}

# =============================================
#  主菜单
# =============================================

main_menu() {
    while true; do
        clear
        echo ""
        echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║     MySQL 8 交互式运维管理工具       ║${NC}"
        echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
        echo ""
        local port
        port=$(get_mysql_port)
        local version
        version=$(run_sql_silent "SELECT VERSION();" 2>/dev/null || echo "N/A")
        info "MySQL 版本: ${version}"
        info "监听端口:   ${port}"
        echo ""
        echo "  1) 用户管理"
        echo "  2) 权限管理"
        echo "  3) 数据库管理"
        echo "  4) 备份与恢复"
        echo "  5) 运维监控"
        echo "  6) 配置管理"
        echo "  0) 退出"
        echo ""
        read -rp "请选择 [0-6]: " choice

        case "$choice" in
            1) menu_user_management ;;
            2) menu_privilege_management ;;
            3) menu_database_management ;;
            4) menu_backup_restore ;;
            5) menu_monitor ;;
            6) menu_config ;;
            0) info "再见!"; exit 0 ;;
            *) warn "无效选择，请重新输入" ;;
        esac
    done
}

# ─── 启动 ───
pre_check
main_menu
