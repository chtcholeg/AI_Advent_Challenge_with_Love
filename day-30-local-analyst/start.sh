#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"

GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m"

log()  { echo -e "${GREEN}[start]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC}  $*"; }
err()  { echo -e "${RED}[error]${NC} $*"; }

# ─── Cleanup on exit ────────────────────────────────────────────────────────
PIDS=()
cleanup() {
  echo ""
  log "Stopping servers..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null && wait "$pid" 2>/dev/null || true
  done
  log "Done. Goodbye."
}
trap cleanup EXIT INT TERM

# ─── Check Ollama ────────────────────────────────────────────────────────────
log "Checking Ollama..."
if curl -s --max-time 2 http://localhost:11434/api/tags > /dev/null 2>&1; then
  log "Ollama is running ✓"
else
  warn "Ollama is NOT running. Start it with:  ollama serve"
  warn "The app will start but LLM features won't work until Ollama is up."
fi

# ─── Backend ─────────────────────────────────────────────────────────────────
log "Starting backend (FastAPI)..."

# Setup venv if needed
if [ ! -d "$BACKEND/.venv" ]; then
  log "Creating Python virtual environment..."
  python3 -m venv "$BACKEND/.venv"
fi

# Activate and install deps
source "$BACKEND/.venv/bin/activate"

# Check if deps are installed (fast check)
if ! python3 -c "import fastapi, uvicorn" 2>/dev/null; then
  log "Installing Python dependencies..."
  pip install -r "$BACKEND/requirements.txt" --quiet
fi

# Copy .env if not exists
if [ ! -f "$BACKEND/.env" ] && [ -f "$ROOT/.env.example" ]; then
  cp "$ROOT/.env.example" "$BACKEND/.env"
  log "Created backend/.env from .env.example"
fi

# Run backend
cd "$BACKEND"
python3 run.py > "$ROOT/backend.log" 2>&1 &
BACKEND_PID=$!
PIDS+=("$BACKEND_PID")
log "Backend started (PID $BACKEND_PID) → http://localhost:8000"

# ─── Frontend ────────────────────────────────────────────────────────────────
log "Starting frontend (Vite)..."

cd "$FRONTEND"

# Install node deps if needed
if [ ! -d "$FRONTEND/node_modules" ]; then
  log "Installing npm dependencies..."
  npm install --silent
fi

npm run dev > "$ROOT/frontend.log" 2>&1 &
FRONTEND_PID=$!
PIDS+=("$FRONTEND_PID")
log "Frontend started (PID $FRONTEND_PID) → http://localhost:5173"

# ─── Wait for frontend to be ready ───────────────────────────────────────────
log "Waiting for servers to be ready..."
WAIT=0
until curl -s --max-time 1 http://localhost:5173 > /dev/null 2>&1; do
  sleep 0.5
  WAIT=$((WAIT + 1))
  if [ $WAIT -ge 30 ]; then
    err "Frontend did not start in time. Check frontend.log"
    break
  fi
done

# Wait for backend
WAIT=0
until curl -s --max-time 1 http://localhost:8000/api/health > /dev/null 2>&1; do
  sleep 0.5
  WAIT=$((WAIT + 1))
  if [ $WAIT -ge 20 ]; then
    err "Backend did not start in time. Check backend.log"
    break
  fi
done

# ─── Open browser ────────────────────────────────────────────────────────────
URL="http://localhost:5173"
log "Opening $URL in browser..."

if command -v open > /dev/null 2>&1; then
  open "$URL"                        # macOS
elif command -v xdg-open > /dev/null 2>&1; then
  xdg-open "$URL"                    # Linux
elif command -v start > /dev/null 2>&1; then
  start "$URL"                       # Windows Git Bash
fi

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  App:     ${GREEN}http://localhost:5173${NC}"
echo -e "  API:     http://localhost:8000/docs"
echo -e "  Logs:    tail -f $ROOT/backend.log"
echo -e "           tail -f $ROOT/frontend.log"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
log "Press Ctrl+C to stop all servers"

# ─── Keep alive ──────────────────────────────────────────────────────────────
wait
