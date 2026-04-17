#!/bin/bash
# ==============================================================================
# My-Panel-Admin Start/Stop Script
# ==============================================================================

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# --- UI Helpers ---
info() { printf "${BLUE}[INFO]${NC} %s\n" "$1"; }
success() { printf "${GREEN}[SUCCESS]${NC} %s\n" "$1"; }
warn() { printf "${YELLOW}[WARN]${NC} %s\n" "$1"; }
error() { printf "${RED}[ERROR]${NC} %s\n" "$1"; }
status_line() { printf "%-40s [%b]\n" "$1" "$2"; }

# --- Dependency Helpers ---
is_pkg_installed() {
    if command -v apt-get >/dev/null 2>&1; then
        dpkg -s "$1" >/dev/null 2>&1
        return $?
    elif command -v yum >/dev/null 2>&1; then
        rpm -q "$1" >/dev/null 2>&1
        return $?
    fi
    return 1
}

# --- Configuration ---
APP_NAME="my-panel-admin"
APP_JAR_PATTERN="my-panel-admin*.jar"

PROXY_NAME="my-panel-proxy"
PROXY_JAR_PATTERN="my-panel-proxy*.jar"

AGENT_NAME="agent"
AGENT_JAR_PATTERN="agent*.jar"

# JDK & Nginx Package Names (for easy modification)
JDK_PKG_NAME="jdk-21-linux.tar.gz"
NGINX_PKG_NAME="nginx-1.24.0.tar.gz"

BIN_DIR=$(cd "$(dirname "$0")"; pwd)
# Try to find the real ROOT_DIR (where the nginx and jdk directories actually are)
ROOT_DIR=$(dirname "$BIN_DIR")
if [ ! -d "$ROOT_DIR/nginx" ] && [ -d "$(dirname "$ROOT_DIR")/nginx" ]; then
    ROOT_DIR=$(dirname "$ROOT_DIR")
fi

# Try to get Nginx port from config
NGINX_PORT=8888
if [ -f "$ROOT_DIR/config/application.yml" ]; then
    EXTRACTED_NGINX_PORT=$(grep -E "^[[:space:]]*nginx-port:" "$ROOT_DIR/config/application.yml" | head -n 1 | awk '{print $2}' | tr -d '\r')
    if [ -n "$EXTRACTED_NGINX_PORT" ]; then
        NGINX_PORT=$EXTRACTED_NGINX_PORT
    fi
fi

# Linux permission check for port 80/443
if [ "$EUID" -ne 0 ] && [ "$NGINX_PORT" -lt 1024 ]; then
    warn "Running as non-root user, but Nginx is configured to port $NGINX_PORT."
    warn "Binding to ports < 1024 requires root privileges. Falling back to port 8888..."
    NGINX_PORT=8888
fi
JDK_DIR="$ROOT_DIR/jdk"
LOG_DIR="$ROOT_DIR/logs"
NGINX_DIR="$ROOT_DIR/nginx"
NGINX_PID_FILE="$ROOT_DIR/nginx.pid"

# JVM Options
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# --- Find JARs ---
APP_JAR_PATH=""
if [ -d "$ROOT_DIR/libs" ]; then
    APP_JAR_PATH=$(ls -t "$ROOT_DIR"/libs/$APP_JAR_PATTERN 2>/dev/null | head -n 1)
fi

if [ -z "$APP_JAR_PATH" ]; then
    if [ -f "$ROOT_DIR/$APP_JAR_PATTERN" ]; then
        APP_JAR_PATH=$(ls -t "$ROOT_DIR"/$APP_JAR_PATTERN | head -n 1)
    fi
fi

if [ -z "$APP_JAR_PATH" ]; then
    error "$APP_JAR_PATTERN not found in root or libs directory."
    exit 1
fi

# Find proxy JAR
PROXY_JAR_PATH=""
if [ -d "$ROOT_DIR/proxy/libs" ]; then
    PROXY_JAR_PATH=$(ls -t "$ROOT_DIR"/proxy/libs/$PROXY_JAR_PATTERN 2>/dev/null | head -n 1)
fi

if [ -z "$PROXY_JAR_PATH" ]; then
    if [ -f "$ROOT_DIR/proxy/$PROXY_JAR_PATTERN" ]; then
        PROXY_JAR_PATH=$(ls -t "$ROOT_DIR"/proxy/$PROXY_JAR_PATTERN | head -n 1)
    fi
fi

if [ -z "$PROXY_JAR_PATH" ]; then
    warn "$PROXY_JAR_PATTERN not found in proxy directory. Proxy service will not be available."
fi

# Find agent JAR
AGENT_JAR_PATH=""
if [ -d "$ROOT_DIR/agent/libs" ]; then
    AGENT_JAR_PATH=$(ls -t "$ROOT_DIR"/agent/libs/$AGENT_JAR_PATTERN 2>/dev/null | head -n 1)
fi

if [ -z "$AGENT_JAR_PATH" ]; then
    if [ -f "$ROOT_DIR/agent/$AGENT_JAR_PATTERN" ]; then
        AGENT_JAR_PATH=$(ls -t "$ROOT_DIR"/agent/$AGENT_JAR_PATTERN | head -n 1)
    fi
fi

if [ -z "$AGENT_JAR_PATH" ]; then
    warn "$AGENT_JAR_PATTERN not found in agent directory. Agent service will not be available."
fi

install() {
    info "Checking dependencies..."

    # 1. Java Detection
    if [ "$TARGET" = "app" ] || [ "$TARGET" = "all" ]; then
        JAVA_CMD=""
        JAVA_SOURCE=""

        # Check System Java
        if command -v java >/dev/null 2>&1; then
            if java -version 2>&1 | grep -q "21"; then
                JAVA_CMD="java"
                JAVA_SOURCE="System Installed Java"
            fi
        fi

        # Check Bundled JDK
        if [ -z "$JAVA_CMD" ]; then
            if [ -d "$JDK_DIR" ]; then
                JDK_PATH=$(find "$JDK_DIR" -maxdepth 3 -name "java" -path "*/bin/java" | head -n 1)

                # If not found, check if there is a tar.gz to extract
                if [ -z "$JDK_PATH" ]; then
                    JDK_ARCHIVE=$(ls "$JDK_DIR"/"$JDK_PKG_NAME" 2>/dev/null | head -n 1)
                    if [ -f "$JDK_ARCHIVE" ]; then
                        # Check if it's a real gzip file before extracting
                        if file "$JDK_ARCHIVE" | grep -q "gzip compressed data"; then
                            info "Extracting bundled JDK..."
                            if tar -xzf "$JDK_ARCHIVE" -C "$JDK_DIR"; then
                                JDK_PATH=$(find "$JDK_DIR" -maxdepth 3 -name "java" -path "*/bin/java" | head -n 1)
                            else
                                error "Failed to extract JDK archive."
                            fi
                        else
                            error "JDK archive is invalid or corrupted (not a gzip file)."
                            info "Please ensure the package was built correctly with Maven."
                            exit 1
                        fi
                    fi
                fi

                if [ -n "$JDK_PATH" ]; then
                    JAVA_CMD="$JDK_PATH"
                    JAVA_HOME=$(dirname $(dirname "$JDK_PATH"))
                    JAVA_SOURCE="Bundled JDK (Internal)"
                fi
            fi
        fi

        if [ -z "$JAVA_CMD" ]; then
            error "JDK 21 not found."
            exit 1
        fi

        info "Java Source:  $JAVA_SOURCE"
        info "Java Path:    $JAVA_CMD"
    fi

    # 2. Nginx Detection/Installation
    if [ "$TARGET" = "nginx" ] || [ "$TARGET" = "all" ]; then
        NGINX_CMD=""
        NGINX_HOME=""

        if [ -d "$NGINX_DIR" ]; then
            FOUND_NGINX=$(find "$NGINX_DIR" -maxdepth 5 -type f -name "nginx" | grep "/sbin/nginx$" | head -n 1)

            if [ -z "$FOUND_NGINX" ]; then
                 NGINX_ARCHIVE=$(ls "$NGINX_DIR"/"$NGINX_PKG_NAME" "$NGINX_DIR"/nginx-*.zip 2>/dev/null | head -n 1)
                 if [ -f "$NGINX_ARCHIVE" ]; then
                     EXTRACT_DIR="$NGINX_DIR/src"
                     mkdir -p "$EXTRACT_DIR"
                     info "Extracting Nginx to $EXTRACT_DIR..."

                     if [[ "$NGINX_ARCHIVE" == *.tar.gz ]]; then
                         if tar -xzf "$NGINX_ARCHIVE" -C "$EXTRACT_DIR" --strip-components=1 2>/dev/null; then
                             info "Extracted Nginx using tar -xzf"
                         else
                             error "Failed to extract Nginx archive $NGINX_ARCHIVE. The file might be corrupted."
                             info "Please ensure the package was built correctly with Maven."
                             exit 1
                         fi
                     elif [[ "$NGINX_ARCHIVE" == *.zip ]]; then
                         unzip -q "$NGINX_ARCHIVE" -d "$EXTRACT_DIR"
                         if [ $(ls -1 "$EXTRACT_DIR" | wc -l) -eq 1 ]; then
                             SUB_DIR=$(ls -d "$EXTRACT_DIR"/*)
                             mv "$SUB_DIR"/* "$EXTRACT_DIR"/
                             rmdir "$SUB_DIR"
                         fi
                     fi

                     if [ -f "$EXTRACT_DIR/configure" ]; then
                         info "Nginx source code detected. Compiling with production-grade modules..."

                         DEPS_OK=0
                        if command -v apt-get >/dev/null 2>&1; then
                            REQ_PKGS=("build-essential" "libpcre3" "libpcre3-dev" "zlib1g" "zlib1g-dev" "libssl-dev" "libxml2-dev" "libxslt1-dev" "libgd-dev")
                            MISSING_PKGS=()
                            for pkg in "${REQ_PKGS[@]}"; do
                                if ! is_pkg_installed "$pkg"; then
                                    MISSING_PKGS+=("$pkg")
                                fi
                            done

                            if [ ${#MISSING_PKGS[@]} -eq 0 ]; then
                                info "All Nginx dependencies are already installed."
                                DEPS_OK=1
                            else
                                info "Missing dependencies: ${MISSING_PKGS[*]}"
                                info "Attempting to install missing dependencies via apt install..."
                                if sudo apt update; then
                                    DEPS_OK=1
                                fi
                                for pkg in "${MISSING_PKGS[@]}"; do
                                    if sudo apt install -y "$pkg"; then
                                        DEPS_OK=1
                                    fi
                                done
                            fi
                        elif command -v yum >/dev/null 2>&1; then
                            REQ_PKGS=("pcre" "pcre-devel" "zlib" "zlib-devel" "openssl" "openssl-devel" "gcc" "gcc-c++" "make" "libxml2-devel" "libxslt-devel" "gd-devel")
                            MISSING_PKGS=()
                            for pkg in "${REQ_PKGS[@]}"; do
                                if ! is_pkg_installed "$pkg"; then
                                    MISSING_PKGS+=("$pkg")
                                fi
                            done

                            if [ ${#MISSING_PKGS[@]} -eq 0 ]; then
                                info "All Nginx dependencies are already installed."
                                DEPS_OK=1
                            else
                                info "Missing dependencies: ${MISSING_PKGS[*]}"
                                info "Attempting to install missing dependencies via yum..."
                                if sudo yum install -y -q "${MISSING_PKGS[@]}"; then
                                    DEPS_OK=1
                                fi
                            fi
                        fi

                         if [ $DEPS_OK -eq 0 ]; then
                             warn "Failed to install dependencies via package manager. Your machine might be offline."
                             info "----------------------------------------------------------------"
                             info "OFFLINE INSTALLATION GUIDE FOR DEPENDENCIES:"
                             info "If your machine is offline, please manually install the following:"
                             info "1. Build Tools: gcc, g++, make"
                             info "2. PCRE Library: libpcre3-dev (Ubuntu) or pcre-devel (CentOS)"
                             info "3. Zlib Library: zlib1g-dev (Ubuntu) or zlib-devel (CentOS)"
                             info "4. OpenSSL Library: libssl-dev (Ubuntu) or openssl-devel (CentOS)"
                             info "5. Optional: libxml2-dev, libxslt1-dev, libgd-dev"
                             info ""
                             info "You can download .deb or .rpm packages from another machine and"
                             info "install them using 'dpkg -i' or 'rpm -ivh'."
                             info "----------------------------------------------------------------"

                             # Check if basic tools exist anyway
                             if command -v gcc >/dev/null 2>&1 && command -v make >/dev/null 2>&1; then
                                 warn "Found gcc and make, attempting to compile anyway..."
                             else
                                 error "Essential build tools (gcc/make) missing. Compilation aborted."
                                 exit 1
                             fi
                         fi

                         cd "$EXTRACT_DIR"
                         ./configure --prefix="$NGINX_DIR" \
                             --with-http_ssl_module \
                             --with-http_v2_module \
                             --with-http_realip_module \
                             --with-http_addition_module \
                             --with-http_sub_module \
                             --with-http_dav_module \
                             --with-http_flv_module \
                             --with-http_mp4_module \
                             --with-http_gunzip_module \
                             --with-http_gzip_static_module \
                             --with-http_random_index_module \
                             --with-http_secure_link_module \
                             --with-http_stub_status_module \
                             --with-http_auth_request_module \
                             --with-threads \
                             --with-stream \
                             --with-stream_ssl_module \
                             --with-stream_ssl_preread_module \
                            --with-pcre-jit \
                            --with-http_slice_module
                        info "Building Nginx (this may take a few minutes)..."
                        make -j$(nproc)
                        make install
                        cd "$BIN_DIR"
                         rm -rf "$EXTRACT_DIR"
                     else
                         info "Binary Nginx package detected. Setting up..."
                         # Check if EXTRACT_DIR has contents before copying
                         if [ "$(ls -A "$EXTRACT_DIR" 2>/dev/null)" ]; then
                             cp -rf "$EXTRACT_DIR"/* "$NGINX_DIR"/
                         else
                             error "Extraction directory $EXTRACT_DIR is empty. Nginx setup failed."
                         fi
                         rm -rf "$EXTRACT_DIR"
                     fi
                     FOUND_NGINX=$(find "$NGINX_DIR" -maxdepth 5 -type f -name "nginx" | grep "/sbin/nginx$" | head -n 1)
                     if [ -n "$FOUND_NGINX" ]; then
                         chmod +x "$FOUND_NGINX"
                     fi
                 fi
            fi
        fi

        if [ -n "$FOUND_NGINX" ] && [ -x "$FOUND_NGINX" ]; then
            NGINX_CMD="$FOUND_NGINX"
            NGINX_HOME=$(dirname $(dirname "$NGINX_CMD"))
            init_nginx_conf
            info "Nginx Path:   $NGINX_CMD"
        fi
    fi

    if [ "$TARGET" = "app" ] || [ "$TARGET" = "all" ]; then
        success "Found JAR: $APP_JAR_PATH"
    fi
    
    if [ "$TARGET" = "proxy" ] || [ "$TARGET" = "all" ]; then
        if [ -n "$PROXY_JAR_PATH" ]; then
            success "Found Proxy JAR: $PROXY_JAR_PATH"
        else
            warn "Proxy JAR not found. Proxy service will not be available."
        fi
    fi
    
    # Replace /tmp/my-panel/admin paths with ROOT_DIR in config files
    info "Updating configuration paths..."
    CONFIG_DIR="$ROOT_DIR/config"
    if [ -d "$CONFIG_DIR" ]; then
        find "$CONFIG_DIR" -type f -name "*.yml" -o -name "*.yaml" -o -name "*.properties" -o -name "*.xml" | while read -r file; do
            if grep -q "/tmp/my-panel/admin" "$file"; then
                sed -i 's|/tmp/my-panel/admin|'"$ROOT_DIR"'|g' "$file"
                info "Updated paths in $file"
            fi
        done
    fi
    
    # Update proxy config paths
    PROXY_CONFIG_DIR="$ROOT_DIR/proxy/config"
    if [ -d "$PROXY_CONFIG_DIR" ]; then
        find "$PROXY_CONFIG_DIR" -type f -name "*.yml" -o -name "*.yaml" -o -name "*.properties" -o -name "*.xml" | while read -r file; do
            if grep -q "/tmp/my-panel/admin" "$file"; then
                sed -i 's|/tmp/my-panel/admin|'"$ROOT_DIR"'|g' "$file"
                info "Updated paths in $file"
            fi
        done
    fi

    # Update agent config paths
    AGENT_CONFIG_DIR="$ROOT_DIR/agent/config"
    if [ -d "$AGENT_CONFIG_DIR" ]; then
        find "$AGENT_CONFIG_DIR" -type f -name "*.yml" -o -name "*.yaml" -o -name "*.properties" -o -name "*.xml" | while read -r file; do
            if grep -q "/tmp/my-panel/admin" "$file"; then
                sed -i 's|/tmp/my-panel/admin|'"$ROOT_DIR"'|g' "$file"
                info "Updated paths in $file"
            fi
        done
    fi

    echo ""
    success "Installation/Check complete."
}

get_pid() {
    local service_name="$1"
    local jar_pattern="$2"
    local pid=""

    # 1. Try jps (most reliable for Java)
    local jps_cmd=""
    if [ -n "$JAVA_CMD" ]; then
        local java_bin_dir=$(dirname "$JAVA_CMD")
        if [ -x "$java_bin_dir/jps" ]; then
            jps_cmd="$java_bin_dir/jps"
        fi
    fi

    if [ -z "$jps_cmd" ] && type jps >/dev/null 2>&1; then
        jps_cmd="jps"
    fi

    if [ -n "$jps_cmd" ]; then
        pid=$($jps_cmd -l | grep "$service_name*" | awk '{print $1}' | head -n 1)
    fi

    # 2. Fallback to ps
    if [ -z "$pid" ]; then
         pid=$(ps -ef | grep "$jar_pattern" | grep -v grep | grep -v "$0" | awk '{print $2}' | head -n 1)
    fi

    echo "$pid"
}

get_app_pids() {
    local pids=()
    
    # 1. Try jps (most reliable for Java)
    local jps_cmd=""
    if [ -n "$JAVA_CMD" ]; then
        local java_bin_dir=$(dirname "$JAVA_CMD")
        if [ -x "$java_bin_dir/jps" ]; then
            jps_cmd="$java_bin_dir/jps"
        fi
    fi

    if [ -z "$jps_cmd" ] && type jps >/dev/null 2>&1; then
        jps_cmd="jps"
    fi

    if [ -n "$jps_cmd" ]; then
        while IFS= read -r line; do
            pids+=($(echo "$line" | awk '{print $1}'))
        done < <($jps_cmd -l | grep "my-panel-admin")
    fi

    # 2. Fallback to ps
    if [ ${#pids[@]} -eq 0 ]; then
        while IFS= read -r line; do
            pids+=($(echo "$line" | awk '{print $2}'))
        done < <(ps -ef | grep "$APP_JAR_PATTERN" | grep -v grep | grep -v "$0")
    fi

    echo "${pids[@]}"
}

get_app_pid() {
    local pids=($(get_app_pids))
    echo "${pids[0]}"
}

get_proxy_pids() {
    local pids=()
    
    # 1. Try jps (most reliable for Java)
    local jps_cmd=""
    if [ -n "$JAVA_CMD" ]; then
        local java_bin_dir=$(dirname "$JAVA_CMD")
        if [ -x "$java_bin_dir/jps" ]; then
            jps_cmd="$java_bin_dir/jps"
        fi
    fi

    if [ -z "$jps_cmd" ] && type jps >/dev/null 2>&1; then
        jps_cmd="jps"
    fi

    if [ -n "$jps_cmd" ]; then
        while IFS= read -r line; do
            pids+=($(echo "$line" | awk '{print $1}'))
        done < <($jps_cmd -l | grep "my-panel-proxy")
    fi

    # 2. Fallback to ps
    if [ ${#pids[@]} -eq 0 ]; then
        while IFS= read -r line; do
            pids+=($(echo "$line" | awk '{print $2}'))
        done < <(ps -ef | grep "$PROXY_JAR_PATTERN" | grep -v grep | grep -v "$0")
    fi

    echo "${pids[@]}"
}

get_proxy_pid() {
    local pids=($(get_proxy_pids))
    echo "${pids[0]}"
}

get_agent_pids() {
    local pids=()

    local jps_cmd=""
    if [ -n "$JAVA_CMD" ]; then
        local java_bin_dir=$(dirname "$JAVA_CMD")
        if [ -x "$java_bin_dir/jps" ]; then
            jps_cmd="$java_bin_dir/jps"
        fi
    fi

    if [ -z "$jps_cmd" ] && type jps >/dev/null 2>&1; then
        jps_cmd="jps"
    fi

    if [ -n "$jps_cmd" ]; then
        while IFS= read -r line; do
            pids+=($(echo "$line" | awk '{print $1}'))
        done < <($jps_cmd -l | grep -E "(AgentApplication|agent-.*\.jar)" | grep -v grep)
    fi

    if [ ${#pids[@]} -eq 0 ]; then
        while IFS= read -r line; do
            pids+=($(echo "$line" | awk '{print $2}'))
        done < <(ps -ef | grep -E "$AGENT_JAR_PATTERN" | grep -v grep | grep -v "$0")
    fi

    echo "${pids[@]}"
}

get_agent_pid() {
    local pids=($(get_agent_pids))
    echo "${pids[0]}"
}

do_start_app() {
    info "Starting $APP_NAME..."
    mkdir -p "$LOG_DIR"
    cd "$ROOT_DIR"
    nohup $JAVA_CMD $JAVA_OPTS -jar "$APP_JAR_PATH" > /dev/null 2>&1 &
    PID=$!
    cd "$BIN_DIR"
    APP_PID_FILE="$ROOT_DIR/$APP_NAME-$PID.pid"
    echo $PID > "$APP_PID_FILE"

    # Wait for application to start (Health Check)
    info "Waiting for $APP_NAME to start..."
    MAX_WAIT=120
    COUNT=0
    SUCCESS=0
    MIN_ALIVE_TIME=30

    while [ $COUNT -lt $MAX_WAIT ]; do
        # Check if process is still running
        if ! kill -0 $PID >/dev/null 2>&1; then
            echo ""
            error "$APP_NAME failed to start. Please check the logs in $LOG_DIR"
            rm -f "$APP_PID_FILE"
            return 1
        fi

        # Check if process is listening on any TCP port
        if command -v lsof >/dev/null 2>&1; then
            lsof -Pan -p $PID -i tcp -sTCP:LISTEN >/dev/null 2>&1 && SUCCESS=1
        elif command -v ss >/dev/null 2>&1; then
            ss -tlnp 2>/dev/null | grep -q "$PID" && SUCCESS=1
        elif command -v netstat >/dev/null 2>&1; then
            netstat -tlpn 2>/dev/null | grep -q "$PID" && SUCCESS=1
        fi

        if [ $SUCCESS -eq 1 ]; then
            echo ""
            success "$APP_NAME started successfully."
            break
        fi

        # Fallback: if process has been alive for MIN_ALIVE_TIME, consider it started
        if [ $COUNT -ge $MIN_ALIVE_TIME ]; then
            echo ""
            success "$APP_NAME started successfully after ${COUNT}s."
            break
        fi

        printf "."
        sleep 1
        COUNT=$((COUNT + 1))
    done

    if [ $SUCCESS -eq 0 ]; then
        echo ""
        error "Timeout: $APP_NAME failed to start within ${MAX_WAIT}s."
        return 1
    fi
}

do_start_proxy() {
    if [ -z "$PROXY_JAR_PATH" ]; then
        warn "Proxy JAR not found. Cannot start proxy service."
        return 1
    fi
    
    info "Starting $PROXY_NAME..."
    mkdir -p "$LOG_DIR"
    cd "$ROOT_DIR/proxy"
    nohup $JAVA_CMD $JAVA_OPTS -jar "$PROXY_JAR_PATH" > /dev/null 2>&1 &
    PID=$!
    cd "$BIN_DIR"
    PROXY_PID_FILE="$ROOT_DIR/$PROXY_NAME-$PID.pid"
    echo $PID > "$PROXY_PID_FILE"

    # Wait for proxy to start (Health Check)
    info "Waiting for $PROXY_NAME to start..."
    MAX_WAIT=120
    COUNT=0
    SUCCESS=0
    MIN_ALIVE_TIME=30

    while [ $COUNT -lt $MAX_WAIT ]; do
        # Check if process is still running
        if ! kill -0 $PID >/dev/null 2>&1; then
            echo ""
            error "$PROXY_NAME failed to start. Please check the logs in $LOG_DIR"
            rm -f "$PROXY_PID_FILE"
            return 1
        fi

        # Check if process is listening on any TCP port
        if command -v lsof >/dev/null 2>&1; then
            lsof -Pan -p $PID -i tcp -sTCP:LISTEN >/dev/null 2>&1 && SUCCESS=1
        elif command -v ss >/dev/null 2>&1; then
            ss -tlnp 2>/dev/null | grep -q "$PID" && SUCCESS=1
        elif command -v netstat >/dev/null 2>&1; then
            netstat -tlpn 2>/dev/null | grep -q "$PID" && SUCCESS=1
        fi

        if [ $SUCCESS -eq 1 ]; then
            echo ""
            success "$PROXY_NAME started successfully."
            break
        fi

        # Fallback: if process has been alive for MIN_ALIVE_TIME, consider it started
        if [ $COUNT -ge $MIN_ALIVE_TIME ]; then
            echo ""
            success "$PROXY_NAME started successfully after ${COUNT}s."
            break
        fi

        printf "."
        sleep 1
        COUNT=$((COUNT + 1))
    done

    if [ $SUCCESS -eq 0 ]; then
        echo ""
        error "Timeout: $PROXY_NAME failed to start within ${MAX_WAIT}s."
        return 1
    fi
}

do_start_agent() {
    if [ -z "$AGENT_JAR_PATH" ]; then
        warn "Agent JAR not found. Cannot start agent service."
        return 1
    fi

    info "Starting $AGENT_NAME..."
    mkdir -p "$LOG_DIR"
    cd "$ROOT_DIR/agent"
    nohup $JAVA_CMD $JAVA_OPTS "-Dlogback.configurationFile=$ROOT_DIR/agent/config/logback.xml" -jar "$AGENT_JAR_PATH" > /dev/null 2>&1 &
    PID=$!
    cd "$BIN_DIR"
    AGENT_PID_FILE="$ROOT_DIR/$AGENT_NAME-$PID.pid"
    echo $PID > "$AGENT_PID_FILE"

    # Wait for agent to start (Health Check)
    info "Waiting for $AGENT_NAME to start..."
    MAX_WAIT=120
    COUNT=0
    SUCCESS=0
    MIN_ALIVE_TIME=30

    while [ $COUNT -lt $MAX_WAIT ]; do
        # Check if process is still running
        if ! kill -0 $PID >/dev/null 2>&1; then
            echo ""
            error "$AGENT_NAME failed to start. Please check the logs in $LOG_DIR"
            rm -f "$AGENT_PID_FILE"
            return 1
        fi

        # Check if process is listening on any TCP port
        if command -v lsof >/dev/null 2>&1; then
            lsof -Pan -p $PID -i tcp -sTCP:LISTEN >/dev/null 2>&1 && SUCCESS=1
        elif command -v ss >/dev/null 2>&1; then
            ss -tlnp 2>/dev/null | grep -q "$PID" && SUCCESS=1
        elif command -v netstat >/dev/null 2>&1; then
            netstat -tlpn 2>/dev/null | grep -q "$PID" && SUCCESS=1
        fi

        if [ $SUCCESS -eq 1 ]; then
            echo ""
            success "$AGENT_NAME started successfully."
            break
        fi

        # Fallback: if process has been alive for MIN_ALIVE_TIME, consider it started
        if [ $COUNT -ge $MIN_ALIVE_TIME ]; then
            echo ""
            success "$AGENT_NAME started successfully after ${COUNT}s."
            break
        fi

        printf "."
        sleep 1
        COUNT=$((COUNT + 1))
    done

    if [ $SUCCESS -eq 0 ]; then
        echo ""
        error "Timeout: $AGENT_NAME failed to start within ${MAX_WAIT}s."
        return 1
    fi
}

init_nginx_conf() {
    CONF_FILE="$NGINX_HOME/conf/nginx.conf"

    if [ -f "$CONF_FILE" ]; then
        if grep -q "# MY-PANEL-ADMIN-CONFIG" "$CONF_FILE"; then
            info "Nginx configuration already exists and is initialized. Skipping."
            return 0
        else
            info "Found existing Nginx config, but it is not initialized for My-Panel-Admin. Overwriting..."
            mv "$CONF_FILE" "${CONF_FILE}.bak"
        fi
    fi

    info "Initializing Nginx (Port: $NGINX_PORT)..."

    # Ensure logs directory exists
    mkdir -p "$NGINX_HOME/logs"

    # Get backend app port from application.yml
    APP_PORT=$(grep -E "^[[:space:]]*port:" "$ROOT_DIR/config/application.yml" | head -n 1 | awk '{print $2}' | tr -d '\r' | xargs)
    if [ -z "$APP_PORT" ]; then
        APP_PORT=8080
        info "Could not determine app port from config, using default: $APP_PORT"
    fi
    info "Backend app port: $APP_PORT"

    # Create new config
    # Use absolute path for pages directory
    PAGES_DIR="$ROOT_DIR/pages"

    cat > "$CONF_FILE" <<EOF
# MY-PANEL-ADMIN-CONFIG
pid $NGINX_PID_FILE;
worker_processes  1;

events {
    worker_connections  1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile        on;
    keepalive_timeout  65;

    server {
        listen       $NGINX_PORT;
        server_name  localhost;

        location / {
            root   $PAGES_DIR;
            index  index.html index.htm;
            try_files \$uri \$uri/ /index.html;
        }

        location /api/ {
            proxy_pass http://localhost:$APP_PORT/;
            proxy_set_header Host \$http_host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }

        location /admin/ {
            proxy_pass http://localhost:$APP_PORT;
            proxy_set_header Host \$http_host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }

        location /swagger-ui/ {
            proxy_pass http://localhost:$APP_PORT;
            proxy_set_header Host \$http_host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }

        location /v3/ {
            proxy_pass http://localhost:$APP_PORT;
            proxy_set_header Host \$http_host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }

        location /druid/ {
            proxy_pass http://localhost:$APP_PORT;
            proxy_set_header Host \$http_host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }

        location /actuator/ {
            proxy_pass http://localhost:$APP_PORT;
            proxy_set_header Host \$http_host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }

        error_page   500 502 503 504  /50x.html;
        location = /50x.html {
            root   html;
        }
    }
}
EOF
    success "Nginx configuration generated."
}

start() {
    echo -e "${BLUE}>>> Starting Services...${NC}"
    # 1. Start Java App
    if [ "$TARGET" = "app" ] || [ "$TARGET" = "all" ]; then
        PID=$(get_app_pid)

        if [ -n "$PID" ]; then
            warn "$APP_NAME is already running (PID: $PID)."
            # Update PID file just in case
            echo "$PID" > "$APP_PID_FILE"
        else
            # Cleanup stale PID file if exists
            if [ -f "$APP_PID_FILE" ]; then
                rm "$APP_PID_FILE"
            fi
            do_start_app
        fi
    fi

    # 2. Start Proxy
    if [ "$TARGET" = "proxy" ] || [ "$TARGET" = "all" ]; then
        if [ -n "$PROXY_JAR_PATH" ]; then
            PID=$(get_proxy_pid)

            if [ -n "$PID" ]; then
                warn "$PROXY_NAME is already running (PID: $PID)."
            fi
            do_start_proxy
        else
            warn "Proxy JAR not found. Cannot start proxy service."
        fi
    fi

    # 3. Start Agent
    if [ "$TARGET" = "agent" ] || [ "$TARGET" = "all" ]; then
        if [ -n "$AGENT_JAR_PATH" ]; then
            PID=$(get_agent_pid)

            if [ -n "$PID" ]; then
                warn "$AGENT_NAME is already running (PID: $PID)."
            fi
            do_start_agent
        else
            warn "Agent JAR not found. Cannot start agent service."
        fi
    fi

    # 4. Start Nginx
    if [ "$TARGET" = "nginx" ] || [ "$TARGET" = "all" ]; then
        if [ -f "$ROOT_DIR/tools/nginx-ops.sh" ]; then
            bash "$ROOT_DIR/tools/nginx-ops.sh" start
        else
            warn "Nginx tools script not found at $ROOT_DIR/tools/nginx-ops.sh"
        fi
    fi
    printf "${BLUE}>>> Start Complete.${NC}\n"
}

stop() {
    printf "${YELLOW}<<< Stopping Services...${NC}\n"
    # 1. Stop Java App
    if [ "$TARGET" = "app" ] || [ "$TARGET" = "all" ]; then
        # Get all possible PIDs
        ALL_PIDS=($(get_app_pids))

        if [ ${#ALL_PIDS[@]} -gt 0 ]; then
            if [ "$TARGET" = "all" ]; then
                # Stop all app processes
                for PID in "${ALL_PIDS[@]}"; do
                    info "Stopping $APP_NAME (PID: $PID)..."

                    # Try graceful stop first
                    kill $PID 2>/dev/null

                    TIMEOUT=10
                    while [ $TIMEOUT -gt 0 ]; do
                        if ! kill -0 $PID >/dev/null 2>&1; then
                            break;
                        fi
                        sleep 1
                        let TIMEOUT=TIMEOUT-1
                        printf "."
                    done

                    if ! kill -0 $PID >/dev/null 2>&1; then
                        printf "\n"
                        success "$APP_NAME (PID: $PID) stopped."
                    else
                        printf "\n"
                        warn "Forcing stop $APP_NAME (PID: $PID)..."
                        kill -9 $PID 2>/dev/null
                        success "$APP_NAME (PID: $PID) force stopped."
                    fi
                done
            else
                # Stop only the first app process
                PID=${ALL_PIDS[0]}
                info "Stopping $APP_NAME (PID: $PID)..."

                # Try graceful stop first
                kill $PID 2>/dev/null

                TIMEOUT=10
                while [ $TIMEOUT -gt 0 ]; do
                    if ! kill -0 $PID >/dev/null 2>&1; then
                        break;
                    fi
                    sleep 1
                    let TIMEOUT=TIMEOUT-1
                    printf "."
                done

                if ! kill -0 $PID >/dev/null 2>&1; then
                    printf "\n"
                    success "$APP_NAME (PID: $PID) stopped."
                else
                    printf "\n"
                    warn "Forcing stop $APP_NAME (PID: $PID)..."
                    kill -9 $PID 2>/dev/null
                    success "$APP_NAME (PID: $PID) force stopped."
                fi
            fi
            # Remove all app PID files
            rm -f "$ROOT_DIR"/my-panel-admin-*.pid
        else
            warn "$APP_NAME is not running."
            # Clean up any stale PID files
            rm -f "$ROOT_DIR"/my-panel-admin-*.pid
        fi
    fi

    # 2. Stop Proxy
    if [ "$TARGET" = "proxy" ] || [ "$TARGET" = "all" ]; then
        # Get all possible PIDs
        ALL_PIDS=($(get_proxy_pids))

        if [ ${#ALL_PIDS[@]} -gt 0 ]; then
            if [ "$TARGET" = "all" ]; then
                # Stop all proxy processes
                for PID in "${ALL_PIDS[@]}"; do
                    info "Stopping $PROXY_NAME (PID: $PID)..."

                    # Try graceful stop first
                    kill $PID 2>/dev/null

                    TIMEOUT=10
                    while [ $TIMEOUT -gt 0 ]; do
                        if ! kill -0 $PID >/dev/null 2>&1; then
                            break;
                        fi
                        sleep 1
                        let TIMEOUT=TIMEOUT-1
                        printf "."
                    done

                    if ! kill -0 $PID >/dev/null 2>&1; then
                        printf "\n"
                        success "$PROXY_NAME (PID: $PID) stopped."
                    else
                        printf "\n"
                        warn "Forcing stop $PROXY_NAME (PID: $PID)..."
                        kill -9 $PID 2>/dev/null
                        success "$PROXY_NAME (PID: $PID) force stopped."
                    fi
                done
            else
                # Stop only the first proxy process
                PID=${ALL_PIDS[0]}
                info "Stopping $PROXY_NAME (PID: $PID)..."

                # Try graceful stop first
                kill $PID 2>/dev/null

                TIMEOUT=10
                while [ $TIMEOUT -gt 0 ]; do
                    if ! kill -0 $PID >/dev/null 2>&1; then
                        break;
                    fi
                    sleep 1
                    let TIMEOUT=TIMEOUT-1
                    printf "."
                done

                if ! kill -0 $PID >/dev/null 2>&1; then
                    printf "\n"
                    success "$PROXY_NAME (PID: $PID) stopped."
                else
                    printf "\n"
                    warn "Forcing stop $PROXY_NAME (PID: $PID)..."
                    kill -9 $PID 2>/dev/null
                    success "$PROXY_NAME (PID: $PID) force stopped."
                fi
            fi
            # Remove all proxy PID files
            rm -f "$ROOT_DIR"/my-panel-proxy-*.pid
        else
            warn "$PROXY_NAME is not running."
            # Clean up any stale PID files
            rm -f "$ROOT_DIR"/my-panel-proxy-*.pid
        fi
    fi

    # 3. Stop Agent
    if [ "$TARGET" = "agent" ] || [ "$TARGET" = "all" ]; then
        ALL_PIDS=($(get_agent_pids))

        if [ ${#ALL_PIDS[@]} -gt 0 ]; then
            if [ "$TARGET" = "all" ]; then
                for PID in "${ALL_PIDS[@]}"; do
                    info "Stopping $AGENT_NAME (PID: $PID)..."

                    kill $PID 2>/dev/null

                    TIMEOUT=10
                    while [ $TIMEOUT -gt 0 ]; do
                        if ! kill -0 $PID >/dev/null 2>&1; then
                            break;
                        fi
                        sleep 1
                        let TIMEOUT=TIMEOUT-1
                        printf "."
                    done

                    if ! kill -0 $PID >/dev/null 2>&1; then
                        printf "\n"
                        success "$AGENT_NAME (PID: $PID) stopped."
                    else
                        printf "\n"
                        warn "Forcing stop $AGENT_NAME (PID: $PID)..."
                        kill -9 $PID 2>/dev/null
                        success "$AGENT_NAME (PID: $PID) force stopped."
                    fi
                done
            else
                PID=${ALL_PIDS[0]}
                info "Stopping $AGENT_NAME (PID: $PID)..."

                kill $PID 2>/dev/null

                TIMEOUT=10
                while [ $TIMEOUT -gt 0 ]; do
                    if ! kill -0 $PID >/dev/null 2>&1; then
                        break;
                    fi
                    sleep 1
                    let TIMEOUT=TIMEOUT-1
                    printf "."
                done

                if ! kill -0 $PID >/dev/null 2>&1; then
                    printf "\n"
                    success "$AGENT_NAME (PID: $PID) stopped."
                else
                    printf "\n"
                    warn "Forcing stop $AGENT_NAME (PID: $PID)..."
                    kill -9 $PID 2>/dev/null
                    success "$AGENT_NAME (PID: $PID) force stopped."
                fi
            fi
            rm -f "$ROOT_DIR"/agent-*.pid
        else
            warn "$AGENT_NAME is not running."
            rm -f "$ROOT_DIR"/agent-*.pid
        fi
    fi

    # 4. Stop Nginx
    if [ "$TARGET" = "nginx" ] || [ "$TARGET" = "all" ]; then
        if [ -f "$ROOT_DIR/tools/nginx-ops.sh" ]; then
            bash "$ROOT_DIR/tools/nginx-ops.sh" stop
        else
            warn "Nginx tools script not found at $ROOT_DIR/tools/nginx-ops.sh"
        fi
    fi
    printf "${YELLOW}<<< Stop Complete.${NC}\n"
}

status() {
    printf "${BLUE}=== Service Status ===${NC}\n"
    # Check App
    if [ "$TARGET" = "app" ] || [ "$TARGET" = "all" ]; then
        PID=$(get_app_pid)
        if [ -n "$PID" ]; then
            status_line "$APP_NAME" "${GREEN}RUNNING (PID: $PID)${NC}"

            # Show listening ports
            if command -v lsof >/dev/null 2>&1; then
                PORTS=$(lsof -Pan -p $PID -i tcp -sTCP:LISTEN | awk "NR>1 {print $9}" | cut -d: -f2 | sort -n | uniq | xargs)
                info "Listening ports: $PORTS"
            elif command -v netstat >/dev/null 2>&1; then
                PORTS=$(netstat -tlpn 2>/dev/null | grep $PID | awk "{print $4}" | awk -F: "{print $NF}" | sort -n | uniq | xargs)
                info "Listening ports: $PORTS"
            fi
        else
            status_line "$APP_NAME" "${RED}STOPPED${NC}"
        fi
    fi

    # Check Proxy
    if [ "$TARGET" = "proxy" ] || [ "$TARGET" = "all" ]; then
        if [ -n "$PROXY_JAR_PATH" ]; then
            PIDS=($(get_proxy_pids))
            if [ ${#PIDS[@]} -gt 0 ]; then
                status_line "$PROXY_NAME" "${GREEN}RUNNING (${#PIDS[@]} instances)${NC}"
                
                # Show each instance
                for PID in "${PIDS[@]}"; do
                    echo "    - Instance (PID: $PID)"
                    
                    # Show listening ports for each instance
                    if command -v lsof >/dev/null 2>&1; then
                        PORTS=$(lsof -Pan -p $PID -i tcp -sTCP:LISTEN | awk "NR>1 {print $9}" | cut -d: -f2 | sort -n | uniq | xargs)
                        if [ -n "$PORTS" ]; then
                            echo "      Listening ports: $PORTS"
                        fi
                    elif command -v netstat >/dev/null 2>&1; then
                        PORTS=$(netstat -tlpn 2>/dev/null | grep $PID | awk "{print $4}" | awk -F: "{print $NF}" | sort -n | uniq | xargs)
                        if [ -n "$PORTS" ]; then
                            echo "      Listening ports: $PORTS"
                        fi
                    fi
                done
            else
                status_line "$PROXY_NAME" "${RED}STOPPED${NC}"
            fi
        else
            status_line "$PROXY_NAME" "${YELLOW}NOT AVAILABLE (JAR not found)${NC}"
        fi
    fi

    # Check Agent
    if [ "$TARGET" = "agent" ] || [ "$TARGET" = "all" ]; then
        if [ -n "$AGENT_JAR_PATH" ]; then
            PIDS=($(get_agent_pids))
            if [ ${#PIDS[@]} -gt 0 ]; then
                status_line "$AGENT_NAME" "${GREEN}RUNNING (${#PIDS[@]} instances)${NC}"

                for PID in "${PIDS[@]}"; do
                    echo "    - Instance (PID: $PID)"

                    if command -v lsof >/dev/null 2>&1; then
                        PORTS=$(lsof -Pan -p $PID -i tcp -sTCP:LISTEN | awk "NR>1 {print $9}" | cut -d: -f2 | sort -n | uniq | xargs)
                        if [ -n "$PORTS" ]; then
                            echo "      Listening ports: $PORTS"
                        fi
                    elif command -v netstat >/dev/null 2>&1; then
                        PORTS=$(netstat -tlpn 2>/dev/null | grep $PID | awk "{print $4}" | awk -F: "{print $NF}" | sort -n | uniq | xargs)
                        if [ -n "$PORTS" ]; then
                            echo "      Listening ports: $PORTS"
                        fi
                    fi
                done
            else
                status_line "$AGENT_NAME" "${RED}STOPPED${NC}"
            fi
        else
            status_line "$AGENT_NAME" "${YELLOW}NOT AVAILABLE (JAR not found)${NC}"
        fi
    fi

    # Check Nginx
    if [ "$TARGET" = "nginx" ] || [ "$TARGET" = "all" ]; then
        if [ -f "$ROOT_DIR/tools/nginx-ops.sh" ]; then
            bash "$ROOT_DIR/tools/nginx-ops.sh" status
        else
            warn "Nginx tools script not found at $ROOT_DIR/tools/nginx-ops.sh"
        fi
    fi
    printf "${BLUE}======================${NC}\n"
}

restart() {
    info "Restarting $TARGET..."
    stop
    sleep 2
    start
}

# --- Main ---
TARGET="all"
ACTION=$1

if [ "$ACTION" = "app" ] || [ "$ACTION" = "proxy" ] || [ "$ACTION" = "agent" ] || [ "$ACTION" = "nginx" ]; then
    TARGET=$ACTION
    ACTION=$2
fi

if [ -z "$ACTION" ]; then
    ACTION="install"
fi

# Pre-check installation for commands other than install
if [ "$ACTION" != "install" ]; then
    # Try to find Java
    JAVA_CHECK=$(find "$JDK_DIR" -maxdepth 3 -name "java" -path "*/bin/java" | head -n 1)
    if [ -z "$JAVA_CHECK" ] && command -v java >/dev/null 2>&1; then
        if java -version 2>&1 | grep -q "21"; then
            JAVA_CHECK="java"
        fi
    fi

    # Try to find Nginx
    NGINX_CHECK=$(find "$NGINX_DIR" -maxdepth 5 -type f -name "nginx" | grep "/sbin/nginx$" | head -n 1)

    # Check dependencies based on TARGET
    if [ "$TARGET" = "app" ] || [ "$TARGET" = "proxy" ] || [ "$TARGET" = "all" ]; then
        if [ -z "$JAVA_CHECK" ]; then
            error "Java dependency is not installed."
            info "Please run: $0 install"
            exit 1
        fi
    fi

    if [ "$TARGET" = "nginx" ] || [ "$TARGET" = "all" ]; then
        if [ -z "$NGINX_CHECK" ]; then
            error "Nginx dependency is not installed."
            info "Please run: $0 install"
            exit 1
        fi
    fi

    # Set variables for the commands
    JAVA_CMD="$JAVA_CHECK"
    NGINX_CMD="$NGINX_CHECK"
    if [ -n "$NGINX_CMD" ]; then
        NGINX_HOME=$(dirname $(dirname "$NGINX_CMD"))
        # Re-initialize to ensure everything is ready
        init_nginx_conf
    fi
fi

case "$ACTION" in
    install)
        install
        ;;
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        error "Unknown action: $ACTION"
        echo "Usage: $0 [app|proxy|agent|nginx] {install|start|stop|restart|status}"
        echo ""
        echo "Example:"
        echo "  $0 app start      # Only start the Java application"
        echo "  $0 proxy status   # Only check Proxy status"
        echo "  $0 agent start    # Only start Agent"
        echo "  $0 nginx start    # Only start Nginx"
        echo "  $0 start          # Start all services"
        echo ""
        echo "Actions:"
        echo "  install  Check/Install dependencies (JDK and Nginx). Default action."
        echo "  start    Start the targeted service(s)"
        echo "  stop     Stop the targeted service(s)"
        echo "  restart  Restart the targeted service(s)"
        echo "  status   Check the status of the targeted service(s)"
        exit 1
        ;;
esac
