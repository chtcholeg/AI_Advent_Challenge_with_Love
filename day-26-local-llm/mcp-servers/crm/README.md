# CRM MCP Server

MCP сервер для интеграции AI-агента с CRM системой (пользователи и тикеты техподдержки).

**Транспорт:** HTTP/SSE (порт 8011)

## Возможности

### Инструменты (Tools)

1. **get_user** - Получить информацию о пользователе по ID
2. **list_users** - Список пользователей с фильтрацией (по статусу, плану подписки)
3. **get_ticket** - Получить информацию о тикете по ID (с информацией о пользователе)
4. **list_tickets** - Список тикетов с фильтрацией (по пользователю, статусу, приоритету, категории)
5. **update_ticket_status** - Обновить статус тикета и добавить заметки
6. **search_tickets** - Поиск тикетов по ключевому слову
7. **get_user_tickets** - Получить все тикеты пользователя с полным контекстом

## Структура данных

### Users (data/users.json)
```json
{
  "id": "user_001",
  "name": "Имя Фамилия",
  "email": "email@example.com",
  "subscriptionPlan": "Pro|Basic|Enterprise",
  "registrationDate": "2024-01-15",
  "lastActive": "2026-02-14",
  "status": "active|inactive"
}
```

### Tickets (data/tickets.json)
```json
{
  "id": "ticket_001",
  "userId": "user_001",
  "subject": "Тема тикета",
  "description": "Описание проблемы",
  "status": "open|in_progress|resolved|closed",
  "priority": "low|medium|high",
  "category": "authentication|indexing|mcp|features|performance|build",
  "created": "2026-02-14T10:30:00Z",
  "updated": "2026-02-14T10:30:00Z",
  "assignedTo": "support_team",
  "notes": "Дополнительные заметки",
  "resolution": "Решение проблемы",
  "resolvedAt": "2026-02-14T12:00:00Z"
}
```

## Установка

1. Установите зависимости:
```bash
cd mcp-servers
pip install -r requirements.txt
```

2. Проверьте наличие данных:
```bash
ls crm/data/
# Должны быть: users.json, tickets.json
```

## Запуск

### Быстрый старт (вместе с Git MCP)
```bash
cd mcp-servers
./START.sh
```

Это запустит оба сервера:
- Git MCP Server на порту 8010
- CRM MCP Server на порту 8011

### Запуск только CRM
```bash
cd mcp-servers/crm
./START.sh
```

### Ручной запуск
```bash
cd mcp-servers
python -m crm.main --no-auth
```

Параметры:
- `--host` - хост (по умолчанию 0.0.0.0)
- `--port` - порт (по умолчанию 8011)
- `--no-auth` - отключить аутентификацию
- `--data-dir` - путь к данным (по умолчанию ./crm/data)

## Использование в AI Agent

### 1. Добавьте MCP сервер в конфигурацию AI Agent

Создайте или отредактируйте файл `~/.ai-agent/mcp-config.json`:

```json
{
  "mcpServers": {
    "crm": {
      "url": "http://localhost:8011/sse",
      "transport": "sse",
      "description": "CRM MCP Server - provides access to user and ticket data"
    }
  }
}
```

Или скопируйте готовую конфигурацию:
```bash
cp support-docs/config/mcp-config.json ~/.ai-agent/
```

### 2. Запустите AI Agent

```bash
./gradlew :ai-agent:run
```

### 3. Включите MCP в настройках

В UI AI Agent:
- Settings → MCP Servers → Enable "crm"

## Примеры использования

### Получить информацию о пользователе
```
AI Agent: Покажи информацию о пользователе user_001
```
Агент вызовет `get_user` с параметром `user_id: "user_001"`

### Найти открытые тикеты с высоким приоритетом
```
AI Agent: Покажи все открытые тикеты с высоким приоритетом
```
Агент вызовет `list_tickets` с параметрами `status: "open", priority: "high"`

### Найти тикеты по ключевому слову
```
AI Agent: Найди тикеты про авторизацию
```
Агент вызовет `search_tickets` с параметром `query: "авторизация"`

### Получить полный контекст пользователя
```
AI Agent: Расскажи про проблемы пользователя user_001
```
Агент вызовет `get_user_tickets` с параметром `user_id: "user_001"`

### Обновить статус тикета
```
AI Agent: Отметь тикет ticket_001 как решенный. Решение: пользователю помог перезапуск.
```
Агент вызовет `update_ticket_status` с параметрами:
```json
{
  "ticket_id": "ticket_001",
  "status": "resolved",
  "resolution": "Пользователю помог перезапуск"
}
```

## Интеграция с RAG

CRM MCP Server отлично работает вместе с RAG:

1. RAG ищет решения в FAQ и документации
2. MCP CRM предоставляет контекст о пользователе и его тикетах
3. AI Agent комбинирует информацию и дает персонализированный ответ

Пример:
```
Пользователь: Почему не работает авторизация?

AI Agent:
1. Проверяет тикет пользователя через CRM MCP
2. Видит, что у пользователя тариф Pro
3. Ищет в FAQ через RAG решения для проблем авторизации
4. Дает ответ с учетом контекста тикета и найденных решений
```

## Расширение

### Добавление новых пользователей
Отредактируйте `data/users.json` и добавьте новый объект.

### Добавление новых тикетов
Отредактируйте `data/tickets.json` и добавьте новый объект.

### Добавление новых инструментов
1. Добавьте Tool в `list_tools()`
2. Добавьте обработку в `call_tool()`
3. Обновите схему inputSchema

## Тестирование

### Тест get_user
```bash
echo '{"method": "tools/call", "params": {"name": "get_user", "arguments": {"user_id": "user_001"}}}' | python -m crm.main
```

### Тест search_tickets
```bash
echo '{"method": "tools/call", "params": {"name": "search_tickets", "arguments": {"query": "авторизация"}}}' | python -m crm.main
```

## Troubleshooting

### Ошибка: "Data files not found"
Убедитесь, что файлы `data/users.json` и `data/tickets.json` существуют.

### Ошибка: "Module 'mcp' not found"
Установите зависимости: `pip install -r requirements.txt`

### AI Agent не видит CRM инструменты
1. Проверьте, что MCP сервер запущен
2. Проверьте конфигурацию в `~/.ai-agent/mcp-config.json`
3. Перезапустите AI Agent
