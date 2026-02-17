# Быстрый старт: Сквозная нумерация и изменение статусов

## Что добавлено

### ✅ Сквозная нумерация задач
- ID задач теперь генерируются последовательно и никогда не повторяются
- Счётчик хранится прямо в файле с задачами

### ✅ Изменение статуса задач (уже было)
- Возможность изменять статус, приоритет и другие поля через `update_task`

## Миграция существующих данных

```bash
cd mcp-servers
python3 pm/migrate_tasks.py
```

Результат:
```
Migrating tasks.json...
Created backup: /path/to/tasks.json.backup
Migration complete!
  Tasks: 12
  Last task ID: 12
```

## Использование

### 1. Создание задачи

Задачи автоматически получают следующий свободный ID:

```bash
# В AI Agent
/task create Добавить новую фичу

# Через API
curl -X POST http://localhost:8012/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "create_task",
      "arguments": {
        "title": "Новая задача",
        "priority": "high"
      }
    }
  }'
```

### 2. Изменение статуса

```bash
# В AI Agent
/task Измени статус task_013 на in_progress

# Через API
curl -X POST http://localhost:8012/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "update_task",
      "arguments": {
        "task_id": "task_013",
        "status": "in_progress",
        "priority": "high",
        "assignee": "developer1"
      }
    }
  }'
```

### 3. Доступные статусы

| Статус | Описание |
|--------|----------|
| `open` | Новая задача |
| `in_progress` | В работе |
| `review` | На ревью |
| `done` | Завершена |
| `blocked` | Заблокирована |
| `cancelled` | Отменена |

### 4. Доступные приоритеты

| Приоритет | Описание |
|-----------|----------|
| `low` | Низкий |
| `medium` | Средний |
| `high` | Высокий |
| `critical` | Критический |

## Проверка

### Посмотреть текущий счётчик

```bash
cat pm/data/tasks.json | python3 -c \
  "import sys, json; data = json.load(sys.stdin); \
   print(f\"Last ID: {data['metadata']['last_task_id']}\")"
```

### Посмотреть все задачи

```bash
# В AI Agent
/task list

# Через API
curl -X POST http://localhost:8012/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "list_tasks",
      "arguments": {}
    }
  }'
```

## Формат данных

### Новый формат tasks.json

```json
{
  "metadata": {
    "last_task_id": 14
  },
  "tasks": [
    {
      "id": "task_013",
      "title": "Тестовая задача",
      "status": "in_progress",
      "priority": "high",
      "assignee": "developer1",
      ...
    }
  ]
}
```

## Примеры команд AI Agent

```
# Создать задачу
/task create Добавить темную тему

# Посмотреть статус проекта
/task status

# Изменить приоритет
/task Установи высокий приоритет для task_013

# Назначить исполнителя
/task Назначь task_014 на developer2

# Завершить задачу
/task Отметь task_013 как выполненную

# Показать дашборд
/task dashboard

# Проанализировать приоритеты (требует GigaChat)
/task priorities
```

## Дополнительная информация

- Полная документация: [SEQUENTIAL_ID_UPDATE.md](SEQUENTIAL_ID_UPDATE.md)
- PM Server README: [README.md](README.md)
- Day 24 документация: [../../day-24-docs/](../../day-24-docs/)
