#!/bin/bash
# selinux-toggle.sh - 开启或关闭 SELinux (CentOS 9)

usage() {
    echo "用法: $0 {enforce|permissive|disable|status}"
    echo "  enforce     - 设置 SELinux 为强制模式 (enforcing)"
    echo "  permissive  - 设置 SELinux 为宽容模式 (permissive)"
    echo "  disable     - 关闭 SELinux (disabled)"
    echo "  status      - 查看当前 SELinux 状态"
    exit 1
}

check_root() {
    if [ "$(id -u)" -ne 0 ]; then
        echo "错误: 需要 root 权限执行此操作"
        exit 1
    fi
}

show_status() {
    echo "=== SELinux 当前状态 ==="
    sestatus
}

set_selinux() {
    local mode=$1
    local config_mode=$2

    check_root

    # 临时生效
    if [ "$mode" != "disabled" ]; then
        setenforce "$mode" 2>/dev/null && echo "临时设置成功: SELinux 模式已切换" || echo "警告: 临时设置失败"
    fi

    # 永久生效（修改配置文件）
    sed -i "s/^SELINUX=.*/SELINUX=$config_mode/" /etc/selinux/config
    echo "永久设置成功: /etc/selinux/config 中 SELINUX=$config_mode"

    if [ "$config_mode" = "disabled" ]; then
        echo "注意: 禁用 SELinux 需要重启系统才能完全生效"
    fi

    show_status
}

case "$1" in
    enforce|enforcing)
        set_selinux 1 enforcing
        ;;
    permissive)
        set_selinux 0 permissive
        ;;
    disable|disabled)
        set_selinux disabled disabled
        ;;
    status)
        show_status
        ;;
    *)
        usage
        ;;
esac
