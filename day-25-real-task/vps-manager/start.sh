#!/usr/bin/env bash
# VPS Manager — быстрый запуск (порт 8000)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$SCRIPT_DIR/backend"
VENV="$BACKEND/.venv"

echo "=== VPS Manager ==="

# Создаём virtualenv если нет
if [ ! -d "$VENV" ]; then
  echo "Создаю virtualenv..."
  python3 -m venv "$VENV"
fi

# Активируем
source "$VENV/bin/activate"

# Устанавливаем зависимости
echo "Устанавливаю зависимости..."
pip install -q -r "$BACKEND/requirements.txt"

echo ""
echo "Запускаю сервер на http://localhost:8000"
echo "Нажмите Ctrl+C для остановки."
echo ""

# Открываем браузер через 1 секунду
(sleep 1 && open "http://localhost:8000" 2>/dev/null || xdg-open "http://localhost:8000" 2>/dev/null || true) &

cd "$BACKEND"
python main.py
