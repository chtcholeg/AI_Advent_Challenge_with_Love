# 🚀 START HERE - Day 15

**Docker + Android Emulator Pipeline - Быстрый старт**

## ⚡ 5 минут до запуска

```bash
# 1. Зависимости
cd mcp-servers && python3 -m venv venv
source venv/bin/activate
pip install sse-starlette starlette uvicorn httpx python-dotenv docker

# 2. Запуск
python launcher.py docker adb --no-auth

# 3. Подключение
# AI Agent → Settings → MCP Servers → Add:
# Docker: http://localhost:8006
# ADB: http://localhost:8007

# 4. Тест
"Покажи Docker контейнеры"
"Запусти Android эмулятор"
```

## 📚 Документация

- **[QUICKSTART.md](QUICKSTART.md)** - Детальный quick start
- **[VPS_SETUP_GUIDE.md](VPS_SETUP_GUIDE.md)** - Настройка VPS
- **[DAY_15_COMPLETE_GUIDE.md](DAY_15_COMPLETE_GUIDE.md)** - Полное руководство
- **[COMMANDS_CHEATSHEET.md](COMMANDS_CHEATSHEET.md)** - Шпаргалка
- **[SUMMARY.md](SUMMARY.md)** - Резюме

## ✅ Проверка

```bash
curl http://localhost:8006/health  # Docker
curl http://localhost:8007/health  # ADB
```

**Готово!** 🎉
