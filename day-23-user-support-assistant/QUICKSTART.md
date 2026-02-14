# Quick Start - User Support Assistant

Быстрый запуск системы техподдержки с RAG + MCP (HTTP/SSE).

## 5-минутный старт

### 1. Проиндексируйте FAQ (выберите один вариант)

**Вариант A: GigaChat Embeddings**
```bash
export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"
```

**Вариант B: Ollama (локальные эмбеддинги)**
```bash
# В отдельном терминале
ollama serve
ollama pull nomic-embed-text

# Запустите GUI
./gradlew :indexer:run
# Выберите: support-docs/faq → support-index.json → Start Indexing
```

### 2. Запустите MCP серверы
```bash
cd mcp-servers
./START.sh
```

Это запустит:
- Git MCP Server: http://localhost:8010
- CRM MCP Server: http://localhost:8011

### 3. Настройте AI Agent

Скопируйте конфигурацию:
```bash
cp support-docs/config/mcp-config.json ~/.ai-agent/
```

### 4. Запустите AI Agent
```bash
./gradlew :ai-agent:run
```

### 5. Настройте в UI

В AI Agent Settings:

**RAG:**
- Enable RAG Mode
- Index Path: `./support-index.json`
- Top K: 5
- Threshold: 0.7

**MCP:**
- Enable "git" server
- Enable "crm" server

**System Prompt:**
Скопируйте из `support-docs/config/support-assistant-prompt.md` (строки 11-200)

### 6. Тестируйте!

Задайте вопрос:
```
Почему не работает авторизация?
```

AI Agent должен:
- Найти решение в FAQ через RAG
- Использовать CRM для контекста пользователя

## Проверка работы

### Проверьте индекс
```bash
./gradlew :shared:runIndexing --args="stats ./support-index.json"
```

### Проверьте MCP серверы
```bash
curl http://localhost:8010/health  # Git MCP
curl http://localhost:8011/health  # CRM MCP
```

### Проверьте логи
```bash
tail -f /tmp/git-mcp.log
tail -f /tmp/crm-mcp.log
```

## Примеры вопросов

### Простые вопросы из FAQ
```
Как установить приложение на Android?
Почему не работает авторизация?
Как настроить MCP серверы?
```

### Вопросы с контекстом пользователя
```
Расскажи про пользователя user_001
Какие проблемы были у user_002?
Найди все тикеты про авторизацию
```

### Комбинированные запросы
```
У user_001 проблема с авторизацией. Помоги решить.
Отметь ticket_001 как решенный. Решение: пользователь обновил креденшелы.
```

## Архитектура

```
User Question
      ↓
AI Agent (Support Mode)
      ↓
┌─────────────┬──────────────┐
│             │              │
▼             ▼              ▼
RAG       CRM MCP      Git MCP
(FAQ)     (Users/      (Git ops)
          Tickets)
      ↓
Персонализированный ответ
```

## Документация

- **Полный Setup:** `support-docs/SETUP_GUIDE.md`
- **Примеры использования:** `support-docs/USAGE_GUIDE.md`
- **HTTP Migration:** `support-docs/MCP_HTTP_MIGRATION.md`
- **Тестовые сценарии:** `support-docs/TEST_SCENARIOS.md`

## Troubleshooting

### RAG не находит информацию
```bash
# Проверьте индекс
./gradlew :shared:runIndexing --args="stats ./support-index.json"

# Переиндексируйте
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"
```

### MCP не подключается
```bash
# Проверьте серверы
curl http://localhost:8010/health
curl http://localhost:8011/health

# Проверьте логи
tail -f /tmp/git-mcp.log
tail -f /tmp/crm-mcp.log

# Перезапустите
cd mcp-servers && ./START.sh
```

### AI Agent не использует инструменты
1. Проверьте System Prompt
2. Проверьте, что MCP серверы enabled
3. Перезапустите AI Agent

## Следующие шаги

После успешного запуска:

1. **Изучите примеры:** `support-docs/USAGE_GUIDE.md`
2. **Добавьте FAQ:** `support-docs/faq/` → переиндексируйте
3. **Добавьте тестовые данные:** `mcp-servers/crm/data/`
4. **Настройте для production:** см. `SETUP_GUIDE.md`
