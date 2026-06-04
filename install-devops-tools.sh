#!/bin/bash
set -e

echo "============================================="
echo "  CentOS 9 常用开发运维工具一键安装脚本"
echo "============================================="

# ─── 权限检查 ───
if [ "$EUID" -ne 0 ]; then
    echo "错误: 请使用 root 用户执行此脚本"
    exit 1
fi

# ─── 检查 dnf 可用 ───
if ! command -v dnf &>/dev/null; then
    echo "错误: 未找到 dnf 包管理器"
    exit 1
fi

echo "更新软件源缓存..."
dnf makecache --refresh >/dev/null 2>&1 || true

# ─── 工具包定义: 命令:包名 ───
# 格式 "检测命令:安装包名:分类"
TOOLS=(
    # ── 编译工具链 ──
    "gcc:gcc:编译工具链"
    "g++:gcc-c++:编译工具链"
    "make:make:编译工具链"
    "cmake:cmake:编译工具链"
    "pkg-config:pkgconf-pkg-config:编译工具链"
    "autoconf:autoconf:编译工具链"
    "automake:automake:编译工具链"
    "libtool:libtool:编译工具链"

    # ── C/C++ 开发库 ──
    "zlib-flate:zlib-devel:C/C++开发库"
    "openssl:openssl-devel:C/C++开发库"
    "readline:readline-devel:C/C++开发库"
    "ncurses:tinfo-devel:C/C++开发库"
    "libcurl:libcurl-devel:C/C++开发库"

    # ── 文本编辑与处理 ──
    "vim:vim-enhanced:文本编辑与处理"
    "nano:nano:文本编辑与处理"
    "emacs:emacs-nox:文本编辑与处理"
    "jq:jq:文本编辑与处理"
    "dos2unix:dos2unix:文本编辑与处理"
    "unix2dos:dos2unix:文本编辑与处理"
    "tree:tree:文本编辑与处理"

    # ── 网络诊断 ──
    "curl:curl:网络诊断"
    "wget:wget:网络诊断"
    "telnet:telnet:网络诊断"
    "nc:nmap-ncat:网络诊断"
    "nslookup:bind-utils:网络诊断"
    "dig:bind-utils:网络诊断"
    "host:bind-utils:网络诊断"
    "traceroute:traceroute:网络诊断"
    "mtr:mtr:网络诊断"
    "tcpdump:tcpdump:网络诊断"
    "ss:iproute:网络诊断"
    "netstat:net-tools:网络诊断"
    "ip:iproute:网络诊断"
    "ethtool:ethtool:网络诊断"
    "nload:nload:网络诊断"
    "iftop:iftop:网络诊断"

    # ── 系统监控与诊断 ──
    "htop:htop:系统监控与诊断"
    "lsof:lsof:系统监控与诊断"
    "fuser:psmisc:系统监控与诊断"
    "sar:sysstat:系统监控与诊断"
    "iostat:sysstat:系统监控与诊断"
    "mpstat:sysstat:系统监控与诊断"
    "vmstat:procps-ng:系统监控与诊断"
    "dstat:dstat:系统监控与诊断"
    "iotop:iotop:系统监控与诊断"
    "strace:strace:系统监控与诊断"
    "ltrace:ltrace:系统监控与诊断"
    "perf:perf:系统监控与诊断"
    "numactl:numactl:系统监控与诊断"
    "ncdu:ncdu:系统监控与诊断"
    "glances:glances:系统监控与诊断"

    # ── 进程与服务管理 ──
    "pstree:psmisc:进程与服务管理"
    "killall:psmisc:进程与服务管理"
    "watch:procps-ng:进程与服务管理"
    "systemctl:systemd:进程与服务管理"
    "journalctl:systemd:进程与服务管理"

    # ── 磁盘与文件 ──
    "ncdu:ncdu:磁盘与文件"
    "du:coreutils:磁盘与文件"
    "df:coreutils:磁盘与文件"
    "lsblk:util-linux:磁盘与文件"
    "fdisk:util-linux:磁盘与文件"
    "parted:parted:磁盘与文件"
    "xfs_info:xfsprogs:磁盘与文件"

    # ── 压缩解压 ──
    "zip:zip:压缩解压"
    "unzip:unzip:压缩解压"
    "bzip2:bzip2:压缩解压"
    "xz:xz:压缩解压"
    "zstd:zstd:压缩解压"
    "p7zip:p7zip:压缩解压"
    "rar:rar:压缩解压"

    # ── 安全与认证 ──
    "ssh:openssh-clients:安全与认证"
    "scp:openssh-clients:安全与认证"
    "rsync:rsync:安全与认证"
    "gpg:gnupg2:安全与认证"
    "openssl:openssl:安全与认证"
    "certutil:nss-tools:安全与认证"

    # ── 版本控制 ──
    "git:git:版本控制"
    "git-lfs:git-lfs:版本控制"

    # ── Python 运行时 ──
    "python3:python3:Python运行时"
    "pip3:python3-pip:Python运行时"

    # ── 杂项 ──
    "screen:screen:杂项"
    "tmux:tmux:杂项"
    "bash-completion:bash-completion:杂项"
    "man:man-pages:杂项"
    "which:which:杂项"
    "lshw:lshw:杂项"
    "dmidecode:dmidecode:杂项"
    "hwloc-ls:hwloc:杂项"
)

# ─── 分类统计 ───
declare -A CATEGORY_INSTALLED
declare -A CATEGORY_SKIPPED
declare -A CATEGORY_FAILED

INSTALLED_COUNT=0
SKIPPED_COUNT=0
FAILED_COUNT=0
FAILED_LIST=()

echo ""
echo "开始检查并安装工具..."
echo "---------------------------------------------"

for item in "${TOOLS[@]}"; do
    IFS=':' read -r CMD PKG CATEGORY <<< "$item"

    if command -v "$CMD" &>/dev/null; then
        echo "  [已有] $CMD"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        CATEGORY_SKIPPED["$CATEGORY"]=$(( ${CATEGORY_SKIPPED["$CATEGORY"]:-0} + 1 ))
    else
        echo -n "  [安装] $CMD <- $PKG ... "
        if dnf install -y "$PKG" >/dev/null 2>&1; then
            # 二次验证
            if command -v "$CMD" &>/dev/null; then
                echo "OK"
                INSTALLED_COUNT=$((INSTALLED_COUNT + 1))
                CATEGORY_INSTALLED["$CATEGORY"]=$(( ${CATEGORY_INSTALLED["$CATEGORY"]:-0} + 1 ))
            else
                # 有些包安装后命令不在 PATH，但包本身安装成功，也算成功
                echo "OK (包已安装，命令可能需重新加载 shell)"
                INSTALLED_COUNT=$((INSTALLED_COUNT + 1))
                CATEGORY_INSTALLED["$CATEGORY"]=$(( ${CATEGORY_INSTALLED["$CATEGORY"]:-0} + 1 ))
            fi
        else
            echo "失败"
            FAILED_COUNT=$((FAILED_COUNT + 1))
            CATEGORY_FAILED["$CATEGORY"]=$(( ${CATEGORY_FAILED["$CATEGORY"]:-0} + 1 ))
            FAILED_LIST+=("$CMD ($PKG)")
        fi
    fi
done

# ─── 安装 EPEL 仓库（部分工具依赖）───
echo ""
echo "确保 EPEL 仓库已启用..."
if ! dnf repolist --enabled | grep -q epel; then
    dnf install -y epel-release >/dev/null 2>&1 || true
fi

# ─── 输出汇总 ───
echo ""
echo "============================================="
echo "  安装完成！汇总报告"
echo "============================================="
echo ""
echo "  已有(跳过): $SKIPPED_COUNT"
echo "  新安装:     $INSTALLED_COUNT"
echo "  安装失败:   $FAILED_COUNT"

if [ ${#FAILED_LIST[@]} -gt 0 ]; then
    echo ""
    echo "  失败列表:"
    for f in "${FAILED_LIST[@]}"; do
        echo "    - $f"
    done
    echo ""
    echo "  提示: 部分工具可能在 EPEL 仓库中，请确保已启用:"
    echo "    dnf install -y epel-release"
fi

echo ""
echo "  分类统计:"
printf "  %-16s %8s %8s %8s\n" "分类" "新安装" "已有" "失败"
echo "  -----------------------------------------------"

# 收集所有分类
ALL_CATEGORIES=$(echo "${!CATEGORY_INSTALLED[@]} ${!CATEGORY_SKIPPED[@]} ${!CATEGORY_FAILED[@]}" | tr ' ' '\n' | sort -u)

for cat in $ALL_CATEGORIES; do
    inst=${CATEGORY_INSTALLED[$cat]:-0}
    skip=${CATEGORY_SKIPPED[$cat]:-0}
    fail=${CATEGORY_FAILED[$cat]:-0}
    printf "  %-16s %8d %8d %8d\n" "$cat" "$inst" "$skip" "$fail"
done

echo "============================================="
