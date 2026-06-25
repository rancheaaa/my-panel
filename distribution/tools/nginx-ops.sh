#!/bin/bash
# ==============================================================================
# Nginx Operations Tool for Linux
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

TOOLS_DIR=$(cd "$(dirname "$0")"; pwd)
ROOT_DIR=$(dirname "$TOOLS_DIR")
# Try to find the real ROOT_DIR (where the nginx directory actually is)
if [ ! -d "$ROOT_DIR/nginx" ] && [ -d "$(dirname "$ROOT_DIR")/nginx" ]; then
    ROOT_DIR=$(dirname "$ROOT_DIR")
fi
NGINX_DIR="$ROOT_DIR/nginx"
NGINX_PID_FILE="$ROOT_DIR/nginx.pid"

# --- Nginx Detection ---
NGINX_CMD=""
NGINX_HOME=""

if [ -d "$NGINX_DIR" ]; then
    # Check for extracted Nginx in embedded directory only
    FOUND_NGINX=$(find "$NGINX_DIR" -maxdepth 5 -type f -name "nginx" | grep "/sbin/nginx$" | head -n 1)
    
    if [ -n "$FOUND_NGINX" ] && [ -x "$FOUND_NGINX" ]; then
        NGINX_CMD="$FOUND_NGINX"
        NGINX_HOME=$(dirname $(dirname "$NGINX_CMD"))
    fi
fi

if [ -z "$NGINX_CMD" ]; then
    error "Embedded Nginx not found. Please ensure it is installed in $NGINX_DIR"
    exit 1
fi

info "Found Embedded Nginx: $NGINX_CMD"
echo ""

start() {
    if pgrep -f "nginx: master process" >/dev/null; then
         warn "Nginx is already running."
    else
         # Set nginx user to current shell user to avoid permission denied
         CONF_FILE="$NGINX_HOME/conf/nginx.conf"
         CURRENT_USER=$(whoami)
         if grep -q "^user " "$CONF_FILE" 2>/dev/null; then
             sed -i "s/^user .*/user $CURRENT_USER;/" "$CONF_FILE"
         else
             sed -i "1i user $CURRENT_USER;" "$CONF_FILE"
         fi
         info "Set nginx user to: $CURRENT_USER"

         info "Checking Nginx config..."
         "$NGINX_CMD" -t -p "$NGINX_HOME"
         if [ $? -ne 0 ]; then
             error "Nginx config invalid."
             if [ "$EUID" -ne 0 ]; then
                 warn "TIP: If the error is 'Permission denied', try running with 'sudo' or use a port > 1024."
             fi
             exit 1
         fi

         info "Starting Nginx..."
         "$NGINX_CMD" -p "$NGINX_HOME"
         
         # Wait a bit and check
         sleep 3
         if pgrep -f "nginx: master process" >/dev/null; then
             success "Nginx started."
         else
             error "Nginx failed to start. Check logs at $NGINX_HOME/logs/error.log"
             exit 1
         fi
    fi
}

stop() {
    info "Stopping Nginx..."
    if pgrep -f "nginx: master process" >/dev/null; then
        # Try graceful stop
        "$NGINX_CMD" -p "$NGINX_HOME" -s stop 2>/dev/null
        
        # Wait and check
        TIMEOUT=5
        while [ $TIMEOUT -gt 0 ]; do
            if ! pgrep -f "nginx: master process" >/dev/null; then
                break
            fi
            sleep 1
            let TIMEOUT=TIMEOUT-1
            printf "."
        done
        
        # If still running, force kill
        if pgrep -f "nginx: master process" >/dev/null; then
            echo ""
            warn "Nginx didn't stop gracefully, force killing..."
            pkill -9 -f "nginx"
        fi
        echo ""
        success "Nginx stopped."
    else
        warn "Nginx is not running."
    fi
}

restart() {
    info "Restarting Nginx..."
    stop
    sleep 2
    start
}

status() {
    if pgrep -f "nginx: master process" >/dev/null; then
        success "Nginx is running."
        info_cmd
    else
        error "Nginx is not running."
    fi
}

info_cmd() {
    CONF_FILE="$NGINX_HOME/conf/nginx.conf"
    info "Configuration File: $CONF_FILE"
    
    if pgrep -f "nginx: master process" >/dev/null; then
        # Try to extract ports from running processes or config
        PORTS=$(grep -E "listen\s+[0-9]+" "$CONF_FILE" | awk '{print $2}' | tr -d ';' | sort -u | tr '\n' ' ')
        info "Listening Ports: ${PORTS:-Unknown (Check config)}"
    else
        warn "Nginx is not running. Cannot detect active ports."
    fi
}

reload() {
    info "Checking Nginx config before reload..."
    "$NGINX_CMD" -t -p "$NGINX_HOME"
    if [ $? -ne 0 ]; then
        error "Nginx config invalid. Reload aborted."
        if [ "$EUID" -ne 0 ]; then
            warn "TIP: If the error is 'Permission denied', try running with 'sudo' or use a port > 1024."
        fi
        exit 1
    fi
    info "Reloading Nginx configuration (Zero Downtime)..."
    "$NGINX_CMD" -p "$NGINX_HOME" -s reload
    if [ $? -eq 0 ]; then
        success "Nginx reloaded successfully."
    else
        error "Failed to reload Nginx."
        exit 1
    fi
}

check() {
    info "Testing Nginx configuration..."
    "$NGINX_CMD" -t -p "$NGINX_HOME"
    if [ $? -eq 0 ]; then
        success "Configuration is valid."
    else
        error "Configuration is invalid."
        if [ "$EUID" -ne 0 ]; then
            warn "TIP: If the error is 'Permission denied', try running with 'sudo' or use a port > 1024."
        fi
        exit 1
    fi
}

case "$1" in
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
    reload)
        reload
        ;;
    check)
        check
        ;;
    info)
        info_cmd
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|reload|check|info}"
        exit 1
esac
