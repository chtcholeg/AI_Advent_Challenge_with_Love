#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/backend"

if [ ! -d ".venv" ]; then
  echo "Creating virtual environment..."
  python3 -m venv .venv
fi

source .venv/bin/activate

echo "Installing dependencies..."
pip install -q -r requirements.txt

echo ""
echo "Starting Ollama (if not running)..."
/opt/homebrew/bin/ollama serve &>/tmp/ollama-day29.log &
sleep 2

echo "Starting LLM Parameter Lab..."
echo "Open: http://localhost:8002"
echo ""
uvicorn main:app --host 0.0.0.0 --port 8002 --reload
