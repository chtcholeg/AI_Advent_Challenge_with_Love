#!/bin/bash
#
# MCP Server Deployment Script
# Usage: ./deploy.sh [VPS_HOST]
#
# Example: ./deploy.sh root@123.45.67.89
#          ./deploy.sh user@mcp.myserver.com
#

set -e

# Configuration
VPS_HOST="${1:-root@YOUR_VPS_IP}"
JAR_PATH="build/libs/mcp-server-all.jar"
REMOTE_PATH="/opt/mcp-server/mcp-server.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if VPS_HOST is set
if [[ "$VPS_HOST" == "root@YOUR_VPS_IP" ]]; then
    echo_error "Please provide VPS host as argument or edit this script"
    echo "Usage: ./deploy.sh user@your-server-ip"
    exit 1
fi

echo_info "Deploying to: $VPS_HOST"

# Step 1: Build
echo_info "Building JAR..."
./gradlew shadowJar --quiet

if [[ ! -f "$JAR_PATH" ]]; then
    echo_error "Build failed: $JAR_PATH not found"
    exit 1
fi

JAR_SIZE=$(ls -lh "$JAR_PATH" | awk '{print $5}')
echo_info "Built: $JAR_PATH ($JAR_SIZE)"

# Step 2: Upload
echo_info "Uploading to server..."
scp -q "$JAR_PATH" "$VPS_HOST:$REMOTE_PATH"

# Step 3: Set permissions and restart
echo_info "Setting permissions and restarting service..."
ssh "$VPS_HOST" << 'EOF'
    chown mcp:mcp /opt/mcp-server/mcp-server.jar
    chmod 640 /opt/mcp-server/mcp-server.jar
    systemctl restart mcp-server
EOF

# Step 4: Wait for startup
echo_info "Waiting for service to start..."
sleep 3

# Step 5: Check status
echo_info "Checking service status..."
ssh "$VPS_HOST" "systemctl is-active mcp-server" && echo_info "Service is running!" || echo_error "Service failed to start"

# Step 6: Health check
echo_info "Running health check..."
HEALTH=$(ssh "$VPS_HOST" "curl -s http://localhost:8081/health" 2>/dev/null || echo "FAILED")

if [[ "$HEALTH" == *"ok"* ]]; then
    echo_info "Health check passed!"
    echo "$HEALTH" | python3 -m json.tool 2>/dev/null || echo "$HEALTH"
else
    echo_warn "Health check may have failed. Check logs:"
    echo "  ssh $VPS_HOST 'journalctl -u mcp-server -n 20'"
fi

echo ""
echo_info "Deployment complete!"
echo_info "View logs: ssh $VPS_HOST 'journalctl -u mcp-server -f'"
