#!/bin/bash
# Multi-server start script for all MCP servers

echo "🚀 MCP Servers - Multi-Server Start"
echo "===================================="
echo ""

# Check Python version
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is required but not found"
    exit 1
fi

PYTHON_VERSION=$(python3 --version | awk '{print $2}')
echo "✓ Python $PYTHON_VERSION found"

# Check Git (for Git MCP Server)
if ! command -v git &> /dev/null; then
    echo "❌ Git is required but not found"
    exit 1
fi

GIT_VERSION=$(git --version | awk '{print $3}')
echo "✓ Git $GIT_VERSION found"
echo ""

# Create venv if not exists
if [ ! -d "venv" ]; then
    echo "📦 Creating virtual environment..."
    python3 -m venv venv
    echo "✓ Virtual environment created"
fi

# Activate venv
echo "🔧 Activating virtual environment..."
source venv/bin/activate

# Install dependencies
if [ ! -f "venv/.installed" ]; then
    echo "📥 Installing dependencies..."
    pip install -q --upgrade pip

    # Install Git MCP Server
    if [ -f "setup.py" ]; then
        pip install -q -e .
    fi

    # Install CRM MCP Server dependencies
    if [ -f "requirements.txt" ]; then
        pip install -q -r requirements.txt
    fi

    touch venv/.installed
    echo "✓ Dependencies installed"
else
    echo "✓ Dependencies already installed"
fi

echo ""
echo "===================================="
echo "🎉 Setup complete!"
echo "===================================="
echo ""

# Auto-detect git repo path
if [ -z "$GIT_REPO_PATH" ]; then
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    GIT_REPO_PATH="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)"
    if [ -z "$GIT_REPO_PATH" ]; then
        echo "⚠  Could not auto-detect git repository. Using current directory."
        GIT_REPO_PATH="$(pwd)"
    fi
    export GIT_REPO_PATH
fi

# CRM data directory
CRM_DATA_DIR="$SCRIPT_DIR/crm/data"

# Check CRM data files
if [ ! -f "$CRM_DATA_DIR/users.json" ] || [ ! -f "$CRM_DATA_DIR/tickets.json" ]; then
    echo "❌ CRM data files not found!"
    echo "   Expected: $CRM_DATA_DIR/users.json, $CRM_DATA_DIR/tickets.json"
    exit 1
fi

echo "Starting MCP Servers:"
echo ""
echo "1. Git MCP Server"
echo "   Repository: $GIT_REPO_PATH"
echo "   URL: http://localhost:8010/sse"
echo ""
echo "2. CRM MCP Server"
echo "   Data: $CRM_DATA_DIR"
echo "   URL: http://localhost:8011/sse"
echo ""

# GitHub token info
if [ -n "$GITHUB_TOKEN" ]; then
    echo "✓ GITHUB_TOKEN is set — git push/pull will authenticate to GitHub"
else
    echo "ℹ  GITHUB_TOKEN is not set — git push/pull will use default credentials"
    echo "   To enable GitHub auth: export GITHUB_TOKEN=\"ghp_xxxxxxxxxxxx\""
fi

echo ""
echo "Press Ctrl+C to stop all servers"
echo ""
echo "===================================="
echo ""

# Function to cleanup background processes
cleanup() {
    echo ""
    echo "Shutting down servers..."
    kill $(jobs -p) 2>/dev/null
    wait
    echo "All servers stopped."
    exit 0
}

trap cleanup SIGINT SIGTERM

# Start Git MCP Server in background
echo "[Git MCP] Starting on port 8010..."
python -m git.main --repo-path "$GIT_REPO_PATH" --no-auth > /tmp/git-mcp.log 2>&1 &
GIT_PID=$!

# Wait a bit for Git server to start
sleep 2

# Start CRM MCP Server in background
echo "[CRM MCP] Starting on port 8011..."
python -m crm.main --no-auth > /tmp/crm-mcp.log 2>&1 &
CRM_PID=$!

# Wait a bit for CRM server to start
sleep 2

echo ""
echo "✓ Servers started!"
echo ""
echo "Git MCP Server: http://localhost:8010 (PID: $GIT_PID)"
echo "CRM MCP Server: http://localhost:8011 (PID: $CRM_PID)"
echo ""
echo "Logs:"
echo "  tail -f /tmp/git-mcp.log"
echo "  tail -f /tmp/crm-mcp.log"
echo ""

# Wait for all background processes
wait
