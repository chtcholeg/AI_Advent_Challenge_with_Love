# User Support Assistant - Setup Guide

Полное руководство по настройке ассистента технической поддержки для Day 23.

## Архитектура

```
┌─────────────────────────────────────────────────────┐
│                  User Question                       │
│          "Почему не работает авторизация?"          │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│              AI Agent (Support Mode)                 │
│  - System Prompt для техподдержки                   │
│  - RAG для поиска в документации                    │
│  - MCP CRM для контекста пользователя               │
└─────────┬───────────────────────┬───────────────────┘
          │                       │
          ▼                       ▼
┌─────────────────────┐ ┌────────────────────────────┐
│   RAG System        │ │    CRM MCP Server          │
│                     │ │                            │
│ FAQ Documents:      │ │ Tools:                     │
│ • authentication.md │ │ • get_user                 │
│ • installation.md   │ │ • get_user_tickets         │
│ • mcp-servers.md    │ │ • search_tickets           │
│ • features.md       │ │ • update_ticket_status     │
│ • errors.md         │ │                            │
│                     │ │ Data:                      │
│ Embeddings:         │ │ • users.json               │
│ • GigaChat API      │ │ • tickets.json             │
│ • Ollama (local)    │ │                            │
└─────────────────────┘ └────────────────────────────┘
          │                       │
          └───────────┬───────────┘
                      ▼
         ┌────────────────────────────┐
         │  Персонализированный ответ │
         │  с учетом:                 │
         │  • FAQ решений             │
         │  • Истории тикетов         │
         │  • Тарифного плана         │
         └────────────────────────────┘
```

## Шаг 1: Подготовка FAQ документации

FAQ документация уже создана в `support-docs/faq/`:
- `authentication.md` - проблемы авторизации
- `installation.md` - установка и запуск
- `mcp-servers.md` - MCP серверы
- `features.md` - функции приложения
- `errors.md` - распространенные ошибки

Эти документы будут проиндексированы для RAG.

## Шаг 2: Индексирование документации

### Вариант A: Использование GigaChat Embeddings

1. Убедитесь, что креденшелы настроены:
```bash
export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"
```

2. Проиндексируйте FAQ:
```bash
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"
```

3. Проверьте индекс:
```bash
./gradlew :shared:runIndexing --args="stats ./support-index.json"
```

### Вариант B: Использование Ollama (локальные эмбеддинги)

1. Установите Ollama:
```bash
# macOS
brew install ollama

# Linux
curl https://ollama.ai/install.sh | sh
```

2. Запустите Ollama:
```bash
ollama serve
```

3. Установите модель для эмбеддингов:
```bash
ollama pull nomic-embed-text
```

4. Запустите Indexer GUI:
```bash
./gradlew :indexer:run
```

5. В GUI:
   - Выберите папку: `support-docs/faq`
   - Форматы: MD
   - Output: `support-index.json`
   - Нажмите "Start Indexing"

## Шаг 3: Настройка CRM MCP Server

1. Установите зависимости:
```bash
cd mcp-servers
pip install -r requirements.txt
```

2. Проверьте данные:
```bash
ls crm/data/
# Должны быть: users.json, tickets.json
```

3. Протестируйте сервер:
```bash
cd mcp-servers
python -m crm.main
# Нажмите Ctrl+C для выхода
```

## Шаг 4: Конфигурация AI Agent

### 4.1 Скопируйте конфигурацию MCP

```bash
mkdir -p ~/.ai-agent
cp support-docs/config/mcp-config.json ~/.ai-agent/
```

### 4.2 Отредактируйте пути (если нужно)

Откройте `~/.ai-agent/mcp-config.json` и убедитесь, что путь правильный:

```json
{
  "mcpServers": {
    "crm": {
      "command": "python",
      "args": ["-m", "crm.main"],
      "cwd": "/полный/путь/к/mcp-servers",
      "env": {}
    }
  }
}
```

## Шаг 5: Настройка System Prompt

1. Откройте `support-docs/config/support-assistant-prompt.md`
2. Скопируйте текст промпта из раздела "Prompt"
3. Запустите AI Agent:
```bash
./gradlew :ai-agent:run
```

4. В UI:
   - Settings → System Prompt
   - Вставьте скопированный промпт
   - Нажмите "Save"

## Шаг 6: Включение RAG

1. В AI Agent Settings:
   - RAG Mode: Enable
   - Index Path: `./support-index.json` (или полный путь)
   - Top K: 5
   - Similarity Threshold: 0.7

2. Нажмите "Save"

## Шаг 7: Включение MCP

1. В AI Agent Settings:
   - MCP Servers → Enable "crm"
   - Проверьте статус подключения

2. Если статус "Disconnected":
   - Запустите MCP сервер вручную в отдельном терминале:
   ```bash
   cd mcp-servers/crm
   ./START.sh
   ```

## Шаг 8: Тестирование

### Тест 1: Простой вопрос из FAQ

Спросите у агента:
```
Почему не работает авторизация?
```

**Ожидаемый результат:**
- Агент использует RAG для поиска в `authentication.md`
- Предоставляет решение из FAQ

### Тест 2: Вопрос с контекстом пользователя

Спросите:
```
Расскажи про проблемы пользователя user_001
```

**Ожидаемый результат:**
- Агент вызывает MCP tool `get_user_tickets`
- Показывает информацию о пользователе и его тикетах

### Тест 3: Комбинированный запрос

Спросите:
```
У пользователя user_001 проблема с авторизацией. Что делать?
```

**Ожидаемый результат:**
- Агент получает контекст пользователя через MCP
- Находит решение через RAG
- Комбинирует информацию в персонализированном ответе

### Тест 4: Поиск тикетов

Спросите:
```
Найди все тикеты про MCP серверы
```

**Ожидаемый результат:**
- Агент вызывает `search_tickets` с query "MCP"
- Показывает найденные тикеты

### Тест 5: Обновление тикета

Спросите:
```
Отметь тикет ticket_001 как решенный. Решение: пользователь обновил креденшелы.
```

**Ожидаемый результат:**
- Агент вызывает `update_ticket_status`
- Обновляет статус и добавляет резолюцию
- Подтверждает обновление

## Troubleshooting

### RAG не находит информацию

**Проблема:** "No relevant context found"

**Решение:**
1. Проверьте, что индекс создан:
```bash
ls -lh support-index.json
```

2. Проверьте статистику индекса:
```bash
./gradlew :shared:runIndexing --args="stats ./support-index.json"
```

3. Попробуйте переиндексировать:
```bash
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"
```

### MCP сервер не подключается

**Проблема:** "CRM MCP Server disconnected"

**Решение:**
1. Запустите сервер вручную:
```bash
cd mcp-servers/crm
./START.sh
```

2. Проверьте логи в терминале

3. Убедитесь, что путь в `~/.ai-agent/mcp-config.json` правильный

### Агент не использует MCP tools

**Проблема:** Агент отвечает без использования CRM данных

**Решение:**
1. Убедитесь, что System Prompt установлен правильно
2. Проверьте, что MCP сервер включен в Settings
3. Попробуйте явно упомянуть user ID в вопросе

### Ошибка эмбеддингов

**Проблема:** "Embedding service error"

**Решение:**

Для GigaChat:
```bash
# Проверьте креденшелы
echo $GIGACHAT_CLIENT_ID
echo $GIGACHAT_CLIENT_SECRET
```

Для Ollama:
```bash
# Проверьте, что Ollama запущен
curl http://localhost:11434/api/tags

# Установите модель
ollama pull nomic-embed-text
```

## Расширение

### Добавление новых FAQ

1. Создайте новый `.md` файл в `support-docs/faq/`
2. Переиндексируйте:
```bash
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"
```
3. Перезапустите AI Agent

### Добавление пользователей/тикетов

Отредактируйте:
- `mcp-servers/crm/data/users.json`
- `mcp-servers/crm/data/tickets.json`

Перезапускать MCP сервер не нужно - изменения применяются автоматически.

### Добавление новых MCP tools

См. `mcp-servers/crm/README.md` раздел "Расширение"

## Production Deployment

### Для production использования:

1. **Замените JSON файлы на реальную БД:**
   - PostgreSQL для пользователей и тикетов
   - Обновите CRM MCP сервер для работы с БД

2. **Настройте аутентификацию:**
   - Добавьте OAuth для доступа к CRM
   - Используйте API токены

3. **Мониторинг:**
   - Логирование всех запросов
   - Метрики использования инструментов
   - Алерты на ошибки

4. **Scaling:**
   - Load balancer для MCP серверов
   - Кеширование частых запросов
   - Rate limiting

## Полезные ссылки

- AI Agent README: `ai-agent/README.md`
- MCP Integration: `docs/MCP_INTEGRATION.md`
- RAG Architecture: `docs/ARCHITECTURE_RU.md`
- CRM MCP Server: `mcp-servers/crm/README.md`
