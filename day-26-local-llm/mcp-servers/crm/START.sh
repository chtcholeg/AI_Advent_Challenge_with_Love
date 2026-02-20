#!/bin/bash

# CRM MCP Server - Quick Start Script (HTTP/SSE)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "🚀 Starting CRM MCP Server..."

# Check if virtual environment exists
if [ ! -d "$SCRIPT_DIR/../venv" ]; then
    echo "⚠️  Virtual environment not found. Creating..."
    cd "$SCRIPT_DIR/.."
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
else
    source "$SCRIPT_DIR/../venv/bin/activate"
fi

# Check if data files exist
if [ ! -f "$SCRIPT_DIR/data/users.json" ] || [ ! -f "$SCRIPT_DIR/data/tickets.json" ]; then
    echo "⚠️  Data files not found!"
    exit 1
fi

echo ""
echo "✅ CRM MCP Server starting on HTTP/SSE..."
echo "   URL: http://localhost:8011/sse"
echo "   Health: http://localhost:8011/health"
echo ""
echo "Press Ctrl+C to stop"
echo ""

cd "$SCRIPT_DIR/.."
python -m crm.main --no-auth
