#!/bin/bash
set -e

echo "============================================="
echo "  SDKMAN 一键安装脚本 (CentOS / Ubuntu)"
echo "============================================="

# ─── 检测是否已安装 ───
# ─── 检测真实用户和 Home 目录 ───
# 如果通过 sudo 执行，获取真实用户的 home 目录
REAL_HOME="$HOME"
if [ -n "$SUDO_USER" ] && [ "$SUDO_USER" != "root" ]; then
    REAL_HOME=$(getent passwd "$SUDO_USER" | cut -d: -f6)
fi

export SDKMAN_DIR="${SDKMAN_DIR:-$REAL_HOME/.sdkman}"

if [ -d "$SDKMAN_DIR/bin" ] && [ -f "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
    echo "SDKMAN 已安装在 $SDKMAN_DIR"
    if source "$SDKMAN_DIR/bin/sdkman-init.sh" 2>/dev/null && command -v sdk &>/dev/null; then
        SDK_VERSION=$(sdk version 2>/dev/null | head -1 || echo "unknown")
        echo "当前版本: $SDK_VERSION"
        echo "如需重装，请先执行: rm -rf $SDKMAN_DIR"
        exit 0
    else
        echo "SDKMAN 目录存在但 sdk 命令不可用，将重新安装..."
        rm -rf "$SDKMAN_DIR"
    fi
fi

# ─── 检测包管理器 ───
if command -v dnf &>/dev/null; then
    PKG_MGR="dnf"
elif command -v yum &>/dev/null; then
    PKG_MGR="yum"
elif command -v apt-get &>/dev/null; then
    PKG_MGR="apt"
else
    PKG_MGR="unknown"
fi

# ─── 依赖包列表 ───
REQUIRED_COMMANDS=(
    "curl:curl"
    "zip:zip"
    "unzip:unzip"
    "tar:tar"
    "sed:sed"
    "grep:grep"
    "awk:gawk"
    "find:findutils"
    "which:which"
)

# apt 包名映射
if [ "$PKG_MGR" = "apt" ]; then
    REQUIRED_COMMANDS=(
        "curl:curl"
        "zip:zip"
        "unzip:unzip"
        "tar:tar"
        "sed:sed"
        "grep:grep"
        "awk:gawk"
        "find:findutils"
        "which:debianutils"
    )
fi

MISSING_PACKAGES=()

echo ""
echo "检查系统依赖..."

for item in "${REQUIRED_COMMANDS[@]}"; do
    CMD="${item%%:*}"
    PKG="${item##*:}"
    if command -v "$CMD" &>/dev/null; then
        echo "  [OK] $CMD"
    else
        echo "  [缺失] $CMD (需要安装 $PKG)"
        MISSING_PACKAGES+=("$PKG")
    fi
done

# ─── 安装缺失依赖 ───
if [ ${#MISSING_PACKAGES[@]} -gt 0 ]; then
    echo ""
    echo "安装缺失依赖: ${MISSING_PACKAGES[*]}"

    if [ "$EUID" -eq 0 ]; then
        # root 用户直接安装
        case "$PKG_MGR" in
            dnf) dnf install -y "${MISSING_PACKAGES[@]}" ;;
            yum) yum install -y "${MISSING_PACKAGES[@]}" ;;
            apt) apt update && apt install -y "${MISSING_PACKAGES[@]}" ;;
            *)   echo "错误: 不支持的包管理器，请手动安装依赖"; exit 1 ;;
        esac
    elif command -v sudo &>/dev/null; then
        # 普通用户通过 sudo 安装
        case "$PKG_MGR" in
            dnf) sudo dnf install -y "${MISSING_PACKAGES[@]}" ;;
            yum) sudo yum install -y "${MISSING_PACKAGES[@]}" ;;
            apt) sudo apt update && sudo apt install -y "${MISSING_PACKAGES[@]}" ;;
            *)   echo "错误: 不支持的包管理器，请手动安装依赖"; exit 1 ;;
        esac
    else
        echo "错误: 非root用户且 sudo 不可用，请手动安装依赖后重试"
        echo "  CentOS/RHEL: dnf install -y ${MISSING_PACKAGES[*]}"
        echo "  Ubuntu/Debian: apt install -y ${MISSING_PACKAGES[*]}"
        exit 1
    fi

    echo "依赖安装完成"
else
    echo "所有依赖已满足"
fi

# ─── 二次验证依赖 ───
echo ""
echo "二次验证依赖..."
ALL_OK=true
for item in "${REQUIRED_COMMANDS[@]}"; do
    CMD="${item%%:*}"
    if ! command -v "$CMD" &>/dev/null; then
        echo "  [失败] $CMD 仍不可用"
        ALL_OK=false
    fi
done

if [ "$ALL_OK" = false ]; then
    echo "错误: 依赖验证失败，请手动安装后重试"
    exit 1
fi
echo "依赖验证通过"

# ─── 安装 SDKMAN ───
echo ""
echo "开始安装 SDKMAN 到 $SDKMAN_DIR ..."

# 用 env 显式传递 HOME 和 SDKMAN_DIR 给子 shell，避免 sudo 场景下 HOME 被重置为 /root
if ! curl -s "https://get.sdkman.io" | env HOME="$REAL_HOME" SDKMAN_DIR="$SDKMAN_DIR" bash; then
    echo "错误: SDKMAN 安装脚本执行失败"
    echo "请检查网络连接，或手动执行: curl -s https://get.sdkman.io | bash"
    exit 1
fi

# ─── 配置环境变量 ───
# 写入真实用户的 ~/.bashrc
SHELL_RC="$REAL_HOME/.bashrc"
MARKER="# >>> sdkman init >>>"
MARKER_END="# <<< sdkman init <<<"

# 移除旧配置
if [ -f "$SHELL_RC" ]; then
    sed -i "/$MARKER/,/$MARKER_END/d" "$SHELL_RC"
fi

cat >> "$SHELL_RC" <<EOF
$MARKER
export SDKMAN_DIR="$SDKMAN_DIR"
[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"
$MARKER_END
EOF

# 如果通过 sudo 执行，修正文件所有权
if [ -n "$SUDO_USER" ] && [ "$SUDO_USER" != "root" ]; then
    chown -R "$SUDO_USER:$SUDO_USER" "$SDKMAN_DIR"
    chown "$SUDO_USER:$SUDO_USER" "$SHELL_RC"
fi

# 如果是 root 用户，也写入 /etc/profile.d 方便所有用户
if [ "$EUID" -eq 0 ]; then
    PROFILE_FILE="/etc/profile.d/sdkman.sh"
    cat > "$PROFILE_FILE" <<EOF
export SDKMAN_DIR="$SDKMAN_DIR"
[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"
EOF
    chmod +x "$PROFILE_FILE"
fi

# ─── 验证安装 ───
echo ""
echo "验证安装..."

source "$SDKMAN_DIR/bin/sdkman-init.sh"

if command -v sdk &>/dev/null; then
    SDK_VERSION=$(sdk version 2>/dev/null | head -1 || echo "unknown")
    echo "  SDKMAN 安装成功！版本: $SDK_VERSION"
else
    echo "  警告: sdk 命令在当前 shell 中不可用"
    echo "  请重新打开终端，或执行: source $SDKMAN_DIR/bin/sdkman-init.sh"
fi

echo ""
echo "============================================="
echo "  SDKMAN 安装完成！"
echo ""
echo "  安装目录: $SDKMAN_DIR"
echo "  环境配置: $SHELL_RC"
echo ""
echo "  使用方法:"
echo "    source ~/.bashrc               # 加载环境"
echo "    sdk list java                  # 查看可用 Java 版本"
echo "    sdk install java 21.0.11-graal # 安装 GraalVM"
echo "    sdk default java 21.0.11-graal # 设为默认"
echo ""
echo "  注意: 新终端会自动加载，当前终端需手动 source"
echo "============================================="
