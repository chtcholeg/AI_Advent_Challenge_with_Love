# Изменения: CRM MCP Server → HTTP/SSE Transport

## Резюме

CRM MCP Server переведен на HTTP/SSE транспорт (как Git MCP Server). Теперь оба сервера запускаются одним скриптом и работают независимо.

## Основные изменения

### 1. CRM MCP Server → HTTP/SSE

**Было:**
- Stdio транспорт
- Запуск через command/args в конфигурации
- Отдельный запуск от Git MCP

**Стало:**
- HTTP/SSE транспорт на порту 8011
- Подключение по URL: `http://localhost:8011/sse`
- Единый запуск с Git MCP через `mcp-servers/START.sh`

### 2. Единый старт-скрипт

**Файл:** `mcp-servers/START.sh`

Запускает оба сервера:
- Git MCP Server (порт 8010)
- CRM MCP Server (порт 8011)

```bash
cd mcp-servers
./START.sh
```

### 3. Упрощенная конфигурация

**Файл:** `support-docs/config/mcp-config.json`

```json
{
  "mcpServers": {
    "git": {
      "url": "http://localhost:8010/sse",
      "transport": "sse"
    },
    "crm": {
      "url": "http://localhost:8011/sse",
      "transport": "sse"
    }
  }
}
```

Больше не нужно указывать пути к Python и рабочие директории!

### 4. Новые файлы

1. **mcp-servers/crm/config.py** - конфигурация HTTP сервера
2. **mcp-servers/crm/main.py** - переписан для HTTP/SSE
3. **support-docs/MCP_HTTP_MIGRATION.md** - гайд по миграции
4. **QUICKSTART.md** - быстрый старт системы
5. **test-mcp-http.sh** - скрипт тестирования

### 5. Обновленная документация

- `mcp-servers/crm/README.md` - добавлена информация о HTTP
- `support-docs/SETUP_GUIDE.md` - обновлен для HTTP транспорта
- `support-docs/config/mcp-config.json` - новый формат конфигурации

## Преимущества

1. **Единый транспорт** - все MCP серверы работают одинаково
2. **Простая конфигурация** - только URL, без paths
3. **Легкий мониторинг** - health endpoints (`:8010/health`, `:8011/health`)
4. **Независимость** - серверы могут работать на разных машинах
5. **Масштабируемость** - легко добавлять новые серверы

## Миграция

### Если вы уже настроили старую версию:

1. **Обновите конфигурацию:**
```bash
cp support-docs/config/mcp-config.json ~/.ai-agent/
```

2. **Запустите серверы:**
```bash
cd mcp-servers
./START.sh
```

3. **Перезапустите AI Agent:**
```bash
./gradlew :ai-agent:run
```

4. **Включите оба MCP сервера в Settings**

### Если настраиваете впервые:

См. **QUICKSTART.md** или **support-docs/SETUP_GUIDE.md**

## Проверка работы

### Быстрая проверка
```bash
curl http://localhost:8010/health  # Git MCP
curl http://localhost:8011/health  # CRM MCP
```

Должны вернуть `{"status":"healthy"}`

### Полная проверка
```bash
./test-mcp-http.sh
```

## Структура проекта

```
mcp-servers/
├── START.sh              # 🆕 Запуск обоих серверов
├── git/
│   ├── main.py           # Git MCP Server (HTTP/SSE)
│   ├── config.py
│   └── START.sh          # Отдельный запуск Git MCP
├── crm/
│   ├── main.py           # 🆕 CRM MCP Server (HTTP/SSE)
│   ├── config.py         # 🆕
│   ├── START.sh          # 🔄 Обновлен для HTTP
│   └── data/
│       ├── users.json
│       └── tickets.json
└── requirements.txt

support-docs/
├── config/
│   ├── mcp-config.json   # 🔄 Обновлен для HTTP
│   └── support-assistant-prompt.md
├── SETUP_GUIDE.md        # 🔄 Обновлен
├── USAGE_GUIDE.md
├── TEST_SCENARIOS.md
└── MCP_HTTP_MIGRATION.md # 🆕

QUICKSTART.md             # 🆕
test-mcp-http.sh          # 🆕
CHANGES.md                # 🆕 Этот файл
```

## Команды

### Запуск
```bash
# Оба сервера
cd mcp-servers && ./START.sh

# Только Git MCP
cd mcp-servers/git && ./START.sh

# Только CRM MCP
cd mcp-servers/crm && ./START.sh
```

### Проверка
```bash
# Health check
curl http://localhost:8010/health
curl http://localhost:8011/health

# Список инструментов
curl http://localhost:8010/tools
curl http://localhost:8011/tools

# Автоматический тест
./test-mcp-http.sh
```

### Логи
```bash
tail -f /tmp/git-mcp.log
tail -f /tmp/crm-mcp.log
```

### Остановка
```bash
# Ctrl+C в терминале с START.sh

# Или
pkill -f "git.main"
pkill -f "crm.main"
```

## Совместимость

- ✅ Работает с существующими данными (`crm/data/*.json`)
- ✅ Работает с существующим RAG индексом
- ✅ Работает с существующим System Prompt
- ⚠️ Требует обновления `~/.ai-agent/mcp-config.json`

## Известные проблемы

Нет. Если обнаружите проблемы:

1. Проверьте логи: `tail -f /tmp/git-mcp.log /tmp/crm-mcp.log`
2. Запустите тест: `./test-mcp-http.sh`
3. См. Troubleshooting в `QUICKSTART.md`

## Следующие шаги

1. **Прочитайте:** `QUICKSTART.md` для быстрого старта
2. **Протестируйте:** Запустите `./test-mcp-http.sh`
3. **Мигрируйте:** Обновите конфигурацию и запустите серверы
4. **Изучите:** `support-docs/MCP_HTTP_MIGRATION.md` для деталей

## Вопросы и проблемы

См. **Troubleshooting** разделы в:
- `QUICKSTART.md`
- `support-docs/SETUP_GUIDE.md`
- `support-docs/MCP_HTTP_MIGRATION.md`

---

## 2026-02-15: Fix - Убран лишний ответ в /support команде

### Проблема
При использовании команды `/support` генерировалось два ответа:
1. Основной ответ от Support Assistant с использованием MCP tools и RAG
2. Лишний ответ: "Документация проекта не содержит дополнительных требований к этим изменениям."

### Причина
Phase 2 (проверка по документации) выполнялась для ВСЕХ команд с `enableTools=true`, включая `/support`. Эта фаза предназначена только для `/review-pr`.

### Решение
Добавлен флаг `requiresDocValidation` в `CommandResult.NeedsLlmProcessing`:
- `/review-pr`: `requiresDocValidation = true` (Phase 2 активна)
- `/support`: `requiresDocValidation = false` (Phase 2 отключена)
- `/help`: `requiresDocValidation = false` (по умолчанию)

### Измененные файлы
- `ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/CommandResult.kt`
- `ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/service/CommandHandler.kt`
- `ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentStore.kt`

---

**Версия:** HTTP/SSE Transport
**Дата:** 2026-02-15
**Обратная совместимость:** Требуется обновление конфигурации
