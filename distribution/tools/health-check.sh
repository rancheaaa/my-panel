#!/bin/bash
# Simple health check script
TOOLS_DIR=$(cd "$(dirname "$0")"; pwd)
ROOT_DIR=$(dirname "$TOOLS_DIR")

# Get port from application.yml
APP_PORT=$(grep -E "^[[:space:]]*port:" "$ROOT_DIR/config/application.yml" | head -n 1 | awk '{print $2}')
if [ -z "$APP_PORT" ]; then
    APP_PORT=8080
fi

echo "Checking application health on port $APP_PORT..."
curl -I http://localhost:$APP_PORT
