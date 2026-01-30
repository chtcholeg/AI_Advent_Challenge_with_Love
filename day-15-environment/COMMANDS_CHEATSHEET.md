# Commands Cheatsheet - Day 15

Быстрая справка по командам для Docker и ADB MCP серверов.

## 🚀 Запуск серверов

```bash
# Активировать виртуальное окружение
cd mcp-servers && source venv/bin/activate

# Запуск Docker MCP Server
python launcher.py docker --no-auth

# Запуск ADB MCP Server
python launcher.py adb --no-auth

# Запуск обоих серверов
python launcher.py docker adb --no-auth

# Список всех серверов
python launcher.py --list
```

## 🐳 Docker Команды (через AI)

### Контейнеры
- "Покажи Docker контейнеры"
- "Запусти контейнер nginx"
- "Останови контейнер nginx"
- "Удали контейнер nginx"

### Логи
- "Покажи логи контейнера nginx"
- "Выполни команду ls -la в контейнере nginx"

### Образы
- "Покажи все Docker образы"
- "Скачай образ nginx"
- "Удали образ nginx"

## 📱 ADB Команды (через AI)

### Устройства
- "Покажи подключенные устройства"
- "Покажи доступные AVD"
- "Запусти эмулятор pixel6_api34"
- "Останови эмулятор"

### Приложения
- "Установи APK из /path/to/app.apk"
- "Сделай скриншот экрана"
- "Покажи информацию об устройстве"

### ADB команды
- "Выполни команду shell ls /sdcard"
- "Покажи последние 50 строк логов"

## 🏗️ Полный Цикл Сборки

```bash
# 1. Сборка Docker образа
cd mcp-servers/docker
docker compose build android-builder

# 2. Запуск контейнера
docker compose up -d android-builder

# 3. Сборка APK (через AI)
"Собери APK: cd workspace/myproject && ./gradlew assembleDebug"

# 4. Извлечение APK
docker cp android_builder:/home/android/workspace/myproject/app/build/outputs/apk/debug/app-debug.apk ./

# 5. Установка (через AI)
"Запусти эмулятор pixel6_api34"
"Установи APK из ./app-debug.apk"
"Сделай скриншот"
```

## 🔧 HTTP API (для отладки)

```bash
# Docker MCP Server
curl http://localhost:8006/health
curl -X POST http://localhost:8006/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# ADB MCP Server
curl http://localhost:8007/health
curl -X POST http://localhost:8007/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

## 🐞 Отладка

```bash
# Docker
docker version && docker ps

# ADB
adb version && adb devices

# Порты
lsof -i :8006 && lsof -i :8007

# Процессы
ps aux | grep python
```
