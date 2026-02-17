# MCP HTTP Migration Guide

Миграция CRM MCP Server с stdio на HTTP/SSE транспорт (как Git MCP Server).

## Что изменилось

### Раньше (stdio)
- CRM MCP сервер работал через stdio транспорт
- Требовал запуск через command/args в конфигурации
- Был изолирован от Git MCP сервера

### Сейчас (HTTP/SSE)
- CRM MCP сервер работает через HTTP/SSE транспорт (порт 8011)
- Подключается по URL, как Git MCP (порт 8010)
- Оба сервера запускаются одним скриптом `mcp-servers/START.sh`

## Преимущества HTTP/SSE

1. **Единый транспорт** - все MCP серверы используют один механизм
2. **Простая конфигурация** - только URL, без paths/commands
3. **Легкий мониторинг** - health endpoints для проверки статуса
4. **Масштабируемость** - серверы могут работать на разных машинах
5. **Независимость** - каждый сервер работает независимо

## Миграция

### Старая конфигурация (~/.ai-agent/mcp-config.json)
```json
{
  "mcpServers": {
    "crm": {
      "command": "python",
      "args": ["-m", "crm.main"],
      "cwd": "/path/to/mcp-servers",
      "env": {}
    }
  }
}
```

### Новая конфигурация
```json
{
  "mcpServers": {
    "git": {
      "url": "http://localhost:8010/sse",
      "transport": "sse",
      "description": "Git MCP Server"
    },
    "crm": {
      "url": "http://localhost:8011/sse",
      "transport": "sse",
      "description": "CRM MCP Server"
    }
  }
}
```

## Быстрый старт

### 1. Обновите конфигурацию
```bash
cp support-docs/config/mcp-config.json ~/.ai-agent/
```

### 2. Запустите оба сервера
```bash
cd mcp-servers
./START.sh
```

### 3. Проверьте работу
```bash
# Git MCP Server
curl http://localhost:8010/health

# CRM MCP Server
curl http://localhost:8011/health
```

Оба должны вернуть `{"status":"healthy", ...}`

### 4. Запустите AI Agent
```bash
./gradlew :ai-agent:run
```

### 5. Включите MCP серверы в настройках
- Settings → MCP Servers → Enable "git"
- Settings → MCP Servers → Enable "crm"

## Запуск серверов

### Вместе (рекомендуется)
```bash
cd mcp-servers
./START.sh
```

Запустит:
- Git MCP Server на порту 8010
- CRM MCP Server на порту 8011

Логи:
- `/tmp/git-mcp.log`
- `/tmp/crm-mcp.log`

### По отдельности

Git MCP:
```bash
cd mcp-servers/git
./START.sh
```

CRM MCP:
```bash
cd mcp-servers/crm
./START.sh
```

## Полезные команды

### Проверка статуса
```bash
# Health check
curl http://localhost:8010/health
curl http://localhost:8011/health

# Список инструментов
curl http://localhost:8010/tools
curl http://localhost:8011/tools
```

### Логи
```bash
# Просмотр логов
tail -f /tmp/git-mcp.log
tail -f /tmp/crm-mcp.log

# Поиск ошибок
grep -i error /tmp/git-mcp.log
grep -i error /tmp/crm-mcp.log
```

### Остановка серверов
```bash
# Если запущены через START.sh - нажмите Ctrl+C

# Или убейте процессы
pkill -f "git.main"
pkill -f "crm.main"
```

## Troubleshooting

### Порт уже занят

**Ошибка:** `Address already in use: 8011`

**Решение:**
```bash
# Найдите процесс
lsof -i :8011

# Остановите его
kill <PID>
```

Или измените порт:
```bash
python -m crm.main --port 8012
```

### Сервер не отвечает

**Проверьте:**
1. Запущен ли сервер: `lsof -i :8011`
2. Логи: `tail -f /tmp/crm-mcp.log`
3. Health endpoint: `curl http://localhost:8011/health`

### AI Agent не видит инструменты

**Решение:**
1. Проверьте конфигурацию: `cat ~/.ai-agent/mcp-config.json`
2. Убедитесь, что серверы запущены: `curl http://localhost:8011/health`
3. Перезапустите AI Agent

## Архитектура

```
┌─────────────────────────────────────────────────┐
│              AI Agent                           │
│                                                 │
│  MCP Client (HTTP/SSE)                         │
└─────────┬────────────────────────┬──────────────┘
          │                        │
          │ HTTP                   │ HTTP
          │ :8010/sse              │ :8011/sse
          ▼                        ▼
┌─────────────────────┐  ┌────────────────────────┐
│  Git MCP Server     │  │  CRM MCP Server        │
│                     │  │                        │
│  Tools:             │  │  Tools:                │
│  • git_status       │  │  • get_user            │
│  • git_diff         │  │  • get_ticket          │
│  • git_commit       │  │  • search_tickets      │
│  • git_push         │  │  • update_ticket       │
│  • ...              │  │  • ...                 │
└─────────────────────┘  └────────────────────────┘
```

## Дополнительная информация

- **Setup Guide:** `support-docs/SETUP_GUIDE.md`
- **Usage Guide:** `support-docs/USAGE_GUIDE.md`
- **CRM README:** `mcp-servers/crm/README.md`
- **Git MCP:** `docs/GIT_MCP_SETUP.md`
