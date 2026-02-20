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

# PM data directory
PM_DATA_DIR="$SCRIPT_DIR/pm/data"

# Check PM data files
if [ ! -f "$PM_DATA_DIR/tasks.json" ] || [ ! -f "$PM_DATA_DIR/projects.json" ]; then
    echo "❌ PM data files not found!"
    echo "   Expected: $PM_DATA_DIR/tasks.json, $PM_DATA_DIR/projects.json"
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
echo "3. Project Management MCP Server"
echo "   Data: $PM_DATA_DIR"
echo "   URL: http://localhost:8012/sse"
echo ""

# GitHub token info
if [ -n "$GITHUB_TOKEN" ]; then
    echo "✓ GITHUB_TOKEN is set — git push/pull will authenticate to GitHub"
else
    echo "ℹ  GITHUB_TOKEN is not set — git push/pull will use default credentials"
    echo "   To enable GitHub auth: export GITHUB_TOKEN=\"ghp_xxxxxxxxxxxx\""
fi

echo ""

# Load GigaChat credentials from local.properties if not set
if [ -z "$GIGACHAT_CLIENT_ID" ] || [ -z "$GIGACHAT_CLIENT_SECRET" ]; then
    LOCAL_PROPS="../local.properties"
    if [ -f "$LOCAL_PROPS" ]; then
        echo "📝 Loading GigaChat credentials from local.properties..."
        export GIGACHAT_CLIENT_ID=$(grep "gigachat.clientId" "$LOCAL_PROPS" | cut -d'=' -f2)
        export GIGACHAT_CLIENT_SECRET=$(grep "gigachat.clientSecret" "$LOCAL_PROPS" | cut -d'=' -f2)
    fi
fi

# GigaChat credentials info (for CRM smart search and PM AI analysis)
if [ -n "$GIGACHAT_CLIENT_ID" ] && [ -n "$GIGACHAT_CLIENT_SECRET" ]; then
    echo "✓ GIGACHAT credentials are set — CRM smart search + PM AI analysis enabled"
else
    echo "ℹ  GIGACHAT credentials not set — CRM will use simple keyword search, PM AI analysis disabled"
    echo "   To enable AI features:"
    echo "   export GIGACHAT_CLIENT_ID=\"your_client_id\""
    echo "   export GIGACHAT_CLIENT_SECRET=\"your_client_secret\""
    echo "   Or add to local.properties:"
    echo "   gigachat.clientId=your_client_id"
    echo "   gigachat.clientSecret=your_client_secret"
    echo "   See: mcp-servers/SMART_SEARCH.md"
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

# Start Git MCP Server in background with logging to both file and stdout
echo "[Git MCP] Starting on port 8010..."
python -m git.main --repo-path "$GIT_REPO_PATH" --no-auth 2>&1 | tee /tmp/git-mcp.log &
GIT_PID=$!

# Wait a bit for Git server to start
sleep 2

echo ""
echo "[CRM MCP] Starting on port 8011..."
python -m crm.main --no-auth 2>&1 | tee /tmp/crm-mcp.log &
CRM_PID=$!

# Wait a bit for CRM server to start
sleep 2

echo ""
echo "[PM MCP] Starting on port 8012..."
python -m pm.main --no-auth 2>&1 | tee /tmp/pm-mcp.log &
PM_PID=$!

# Wait a bit for PM server to start
sleep 2

echo ""
echo "✓ Servers started!"
echo ""
echo "Git MCP Server: http://localhost:8010 (PID: $GIT_PID)"
echo "CRM MCP Server: http://localhost:8011 (PID: $CRM_PID)"
echo "PM MCP Server:  http://localhost:8012 (PID: $PM_PID)"
echo ""
echo "Logs also saved to:"
echo "  /tmp/git-mcp.log"
echo "  /tmp/crm-mcp.log"
echo "  /tmp/pm-mcp.log"
echo ""

# Wait for all background processes
wait
