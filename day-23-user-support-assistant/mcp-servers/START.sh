#!/bin/bash
# Quick start script for Git MCP Server

echo "🚀 Git MCP Server - Quick Start"
echo "================================"
echo ""

# Check Python version
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is required but not found"
    exit 1
fi

PYTHON_VERSION=$(python3 --version | awk '{print $2}')
echo "✓ Python $PYTHON_VERSION found"

# Check Git
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
    pip install -q -e .
    touch venv/.installed
    echo "✓ Dependencies installed"
else
    echo "✓ Dependencies already installed"
fi

echo ""
echo "================================"
echo "🎉 Setup complete!"
echo "================================"
echo ""
# Auto-detect git repo path: use GIT_REPO_PATH if set, otherwise find the repo root
if [ -z "$GIT_REPO_PATH" ]; then
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    GIT_REPO_PATH="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)"
    if [ -z "$GIT_REPO_PATH" ]; then
        echo "⚠  Could not auto-detect git repository. Using current directory."
        GIT_REPO_PATH="$(pwd)"
    fi
    export GIT_REPO_PATH
fi

echo "Starting Git MCP Server..."
echo "Repository: $GIT_REPO_PATH"
echo "URL: http://localhost:8010/sse"
echo ""

# GitHub token info
if [ -n "$GITHUB_TOKEN" ]; then
    echo "✓ GITHUB_TOKEN is set — git push/pull will authenticate to GitHub"
else
    echo "ℹ  GITHUB_TOKEN is not set — git push/pull will use default credentials"
    echo "   To enable GitHub auth: export GITHUB_TOKEN=\"ghp_xxxxxxxxxxxx\""
fi

echo ""
echo "Press Ctrl+C to stop"
echo ""

# Start server
python -m git.main --repo-path "$GIT_REPO_PATH"
