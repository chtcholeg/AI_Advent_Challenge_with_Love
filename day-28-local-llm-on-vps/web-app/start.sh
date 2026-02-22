#!/usr/bin/env bash
# JuriLytics (Фаза 2) — быстрый запуск

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$SCRIPT_DIR/backend"
VENV="$BACKEND/.venv"

echo "=== JuriLytics ==="

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

# Берём .env из prototype/ если в backend/ нет своего
if [ ! -f "$BACKEND/.env" ] && [ -f "$SCRIPT_DIR/../prototype/.env" ]; then
  echo "Копирую .env из prototype/..."
  cp "$SCRIPT_DIR/../prototype/.env" "$BACKEND/.env"
fi

if [ ! -f "$BACKEND/.env" ]; then
  echo ""
  echo "ВНИМАНИЕ: Не найден .env файл."
  echo "Создайте файл $BACKEND/.env:"
  echo "  GIGACHAT_AUTHORIZATION_KEY=<ваш_ключ>"
  echo "  ADMIN_USERNAME=admin"
  echo "  ADMIN_PASSWORD=<ваш_пароль>"
  echo ""
fi

echo ""
echo "Запускаю сервер на http://localhost:8001"
echo "Нажмите Ctrl+C для остановки."
echo ""

# Открываем браузер через 1 секунду
(sleep 1 && open "http://localhost:8001" 2>/dev/null || xdg-open "http://localhost:8001" 2>/dev/null || true) &

cd "$BACKEND"
python main.py
