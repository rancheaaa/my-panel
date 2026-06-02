#!/bin/bash
set -e

echo "============================================="
echo "  SDKMAN 一键安装脚本 (CentOS 9 / RHEL 9)"
echo "============================================="

# ─── 权限检查 ───
if [ "$EUID" -ne 0 ]; then
    echo "错误: 请使用 root 用户执行此脚本"
    exit 1
fi

# ─── 检测是否已安装 ───
SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"

if [ -d "$SDKMAN_DIR/bin" ] && [ -f "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
    echo "SDKMAN 已安装在 $SDKMAN_DIR"
    # 检查 sdk 命令是否可用
    if source "$SDKMAN_DIR/bin/sdkman-init.sh" 2>/dev/null && command -v sdk &>/dev/null; then
        SDK_VERSION=$(sdk version 2>/dev/null | head -1 || echo "unknown")
        echo "当前版本: $SDK_VERSION"
        echo "如需重装，请先执行: rm -rf $SDKMAN_DIR && rm -f /etc/profile.d/sdkman.sh"
        exit 0
    else
        echo "SDKMAN 目录存在但 sdk 命令不可用，将重新安装..."
        rm -rf "$SDKMAN_DIR"
    fi
fi

# ─── 检测目标用户 ───
# 如果通过 sudo 执行，安装到实际用户目录
TARGET_HOME="$HOME"
TARGET_USER="$USER"
if [ -n "$SUDO_USER" ] && [ "$SUDO_USER" != "root" ]; then
    TARGET_HOME=$(getent passwd "$SUDO_USER" | cut -d: -f6)
    TARGET_USER="$SUDO_USER"
    SDKMAN_DIR="$TARGET_HOME/.sdkman"
    echo "检测到 sudo 执行，SDKMAN 将安装到用户 $TARGET_USER 的目录: $SDKMAN_DIR"
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
    "git:git"
)

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
    if ! dnf install -y "${MISSING_PACKAGES[@]}"; then
        echo "错误: 依赖安装失败，请检查 dnf 源是否正常"
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
echo "开始安装 SDKMAN..."

# 使用非交互式安装
export SDKMAN_DIR
if ! curl -s "https://get.sdkman.io" | bash; then
    echo "错误: SDKMAN 安装脚本执行失败"
    echo "请检查网络连接，或手动执行: curl -s https://get.sdkman.io | bash"
    exit 1
fi

# ─── 配置环境变量 ───
# 写入 /etc/profile.d/ 确保所有用户 shell 都能加载
PROFILE_FILE="/etc/profile.d/sdkman.sh"
cat > "$PROFILE_FILE" <<EOF
export SDKMAN_DIR="$SDKMAN_DIR"
[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"
EOF
chmod +x "$PROFILE_FILE"

# 如果目标用户不是 root，修正文件所有权
if [ -n "$SUDO_USER" ] && [ "$SUDO_USER" != "root" ]; then
    chown -R "$TARGET_USER:$TARGET_USER" "$SDKMAN_DIR"
    chown "$TARGET_USER:$TARGET_USER" "$PROFILE_FILE"
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
echo "  环境配置: $PROFILE_FILE"
echo ""
echo "  使用方法:"
echo "    source $PROFILE_FILE        # 加载环境"
echo "    sdk list java               # 查看可用 Java 版本"
echo "    sdk install java 21.0.11-graal  # 安装 GraalVM"
echo "    sdk default java 21.0.11-graal  # 设为默认"
echo ""
echo "  注意: 新终端会自动加载，当前终端需手动 source"
echo "============================================="
