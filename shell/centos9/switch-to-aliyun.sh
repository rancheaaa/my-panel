#!/bin/bash
set -e

BACKUP_DIR="/etc/yum.repos.d/bak_$(date +%Y%m%d_%H%M%S)"
REPO_DIR="/etc/yum.repos.d"
MIRROR_BASE="https://mirrors.aliyun.com"

echo "============================================="
echo "  CentOS 9 Stream / Rocky 9 / AlmaLinux 9"
echo "  一键切换阿里云源"
echo "============================================="

if [ "$EUID" -ne 0 ]; then
    echo "错误: 请使用 root 用户执行此脚本"
    exit 1
fi

OS_ID=$(grep '^ID=' /etc/os-release | cut -d= -f2 | tr -d '"' | tr '[:upper:]' '[:lower:]')
OS_VERSION_ID=$(grep '^VERSION_ID=' /etc/os-release | cut -d= -f2 | tr -d '"' | cut -d. -f1)

case "$OS_ID" in
    centos)
        DISTRO="centos-stream"
        ;;
    rocky)
        DISTRO="rocky"
        ;;
    almalinux)
        DISTRO="almalinux"
        ;;
    *)
        echo "不支持的操作系统: $OS_ID，仅支持 centos/rocky/almalinux"
        exit 1
        ;;
esac

echo "检测到系统: $OS_ID $OS_VERSION_ID"

mkdir -p "$BACKUP_DIR"
echo "备份原有 repo 文件到 $BACKUP_DIR ..."
cp -f ${REPO_DIR}/*.repo "$BACKUP_DIR/" 2>/dev/null || true
cp -f ${REPO_DIR}/*.repo.bak "$BACKUP_DIR/" 2>/dev/null || true

rm -f ${REPO_DIR}/centos-*.repo ${REPO_DIR}/rocky-*.repo ${REPO_DIR}/almalinux-*.repo \
       ${REPO_DIR}/CentOS-Stream-* ${REPO_DIR}/Rocky-* ${REPO_DIR}/AlmaLinux-* \
       ${REPO_DIR}/epel*.repo 2>/dev/null || true

case "$DISTRO" in
    centos-stream)
        cat > "${REPO_DIR}/centos-stream.repo" <<'EOF'
[baseos]
name=CentOS Stream $releasever - BaseOS
baseurl=https://mirrors.aliyun.com/centos-stream/9-stream/BaseOS/$basearch/os/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial

[appstream]
name=CentOS Stream $releasever - AppStream
baseurl=https://mirrors.aliyun.com/centos-stream/9-stream/AppStream/$basearch/os/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial

[crb]
name=CentOS Stream $releasever - CRB
baseurl=https://mirrors.aliyun.com/centos-stream/9-stream/CRB/$basearch/os/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial
EOF
        ;;

    rocky)
        cat > "${REPO_DIR}/rocky.repo" <<'EOF'
[baseos]
name=Rocky Linux $releasever - BaseOS
mirrorlist=https://mirrors.rockylinux.org/mirrorlist?arch=$basearch&release=$releasever&repo=BaseOS
# baseurl=http://dl.rockylinux.org/$releasever/BaseOS/$basearch/os/
baseurl=https://mirrors.aliyun.com/rockylinux/$releasever/BaseOS/$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/ROCKY-GPG-KEY-RPM-GPG-KEY-Rocky-9

[appstream]
name=Rocky Linux $releasever - AppStream
mirrorlist=https://mirrors.rockylinux.org/mirrorlist?arch=$basearch&release=$releasever&repo=AppStream
# baseurl=http://dl.rockylinux.org/$releasever/AppStream/$basearch/os/
baseurl=https://mirrors.aliyun.com/rockylinux/$releasever/AppStream/$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-RPM-GPG-KEY-Rocky-9

[crb]
name=Rocky Linux $releasever - CRB
mirrorlist=https://mirrors.rockylinux.org/mirrorlist?arch=$basearch&release=$releasever&repo=CRB
# baseurl=http://dl.rockylinux.org/$releasever/CRB/$basearch/os/
baseurl=https://mirrors.aliyun.com/rockylinux/$releasever/CRB/$basearch/os/
gpgcheck=1
enabled=0
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-RPM-GPG-KEY-Rocky-9
EOF
        ;;

    almalinux)
        cat > "${REPO_DIR}/almalinux.repo" <<'EOF'
[baseos]
name=AlmaLinux $releasever - BaseOS
mirrorlist=https://mirrors.almalinux.org/mirrorlist/$releasever/baseos
# baseurl=https://repo.almalinux.org/almalinux/$releasever/BaseOS/$basearch/os/
baseurl=https://mirrors.aliyun.com/almalinux/$releasever/BaseOS/$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-AlmaLinux-9

[appstream]
name=AlmaLinux $releasever - AppStream
mirrorlist=https://mirrors.almalinux.org/mirrorlist/$releasever/appstream
# baseurl=https://repo.almalinux.org/almalinux/$releasever/AppStream/$basearch/os/
baseurl=https://mirrors.aliyun.com/almalinux/$releasever/AppStream/$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-AlmaLinux-9

[crb]
name=AlmaLinux $releasever - CRB
mirrorlist=https://mirrors.almalinux.org/mirrorlist/$releasever/crb
# baseurl=https://repo.almalinux.org/almalinux/$releasever/CRB/$basearch/os/
baseurl=https://mirrors.aliyun.com/almalinux/$releasever/CRB/$basearch/os/
gpgcheck=1
enabled=0
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-AlmaLinux-9
EOF
        ;;
esac

cat > "${REPO_DIR}/epel.repo" <<'EOF'
[epel]
name=Extra Packages for Enterprise Linux $releasever - $basearch
baseurl=https://mirrors.aliyun.com/epel/$releasever/Everything/$basearch/
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/epel/RPM-GPG-KEY-EPEL-$releasever
countme=1

[epel-debuginfo]
name=Extra Packages for Enterprise Linux $releasever - $basearch - Debug
baseurl=https://mirrors.aliyun.com/epel/$releasever/Everything/$basearch/debug/
enabled=0
gpgkey=https://mirrors.aliyun.com/epel/RPM-GPG-KEY-EPEL-$releasever
gpgcheck=1

[epel-source]
name=Extra Packages for Enterprise Linux $releasever - $basearch - Source
baseurl=https://mirrors.aliyun.com/epel/$releasever/Everything/SRPMS/
enabled=0
gpgkey=https://mirrors.aliyun.com/epel/RPM-GPG-KEY-EPEL-$releasever
gpgcheck=1
EOF

echo "清理缓存..."
dnf clean all >/dev/null 2>&1 || true

echo "生成新缓存..."
dnf makecache --refresh

echo ""
echo "============================================="
echo "  阿里云源配置完成！"
echo "  原有 repo 已备份至: $BACKUP_DIR"
echo "============================================="
echo ""
echo "验证：执行以下命令测试"
echo "  dnf repolist"
