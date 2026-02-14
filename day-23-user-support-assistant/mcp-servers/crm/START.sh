#!/bin/bash

# CRM MCP Server - Quick Start Script

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "🚀 Starting CRM MCP Server..."

# Check if virtual environment exists
if [ ! -d "../venv" ]; then
    echo "⚠️  Virtual environment not found. Creating..."
    cd ..
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
    cd crm
else
    source ../venv/bin/activate
fi

# Check if data files exist
if [ ! -f "data/users.json" ] || [ ! -f "data/tickets.json" ]; then
    echo "⚠️  Data files not found!"
    exit 1
fi

echo "✅ CRM MCP Server starting on stdio..."
python -m crm.main

# Keep the script running
wait
