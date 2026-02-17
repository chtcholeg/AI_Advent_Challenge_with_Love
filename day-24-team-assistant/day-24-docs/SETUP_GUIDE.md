# Team Assistant - Setup Guide

Пошаговое руководство по настройке Team Assistant (Day 24).

## Предварительные требования

- JDK 17+
- Python 3.8+
- Git
- GigaChat API credentials (для AI-анализа)

## Шаг 1: Настройка Project Management MCP Server

### 1.1. Проверка структуры

Убедитесь, что структура директорий создана:

```bash
cd mcp-servers
ls -la pm/
```

Должны быть:
- `pm/main.py` - основной сервер
- `pm/config.py` - конфигурация
- `pm/ai_service.py` - AI-анализ
- `pm/dashboard_service.py` - дашборд
- `pm/data/tasks.json` - задачи
- `pm/data/projects.json` - проекты
- `pm/requirements.txt` - зависимости

### 1.2. Установка зависимостей

```bash
cd mcp-servers

# Создать виртуальное окружение (если ещё не создано)
python -m venv venv

# Активировать
source venv/bin/activate  # Linux/macOS
# или
venv\Scripts\activate     # Windows

# Установить зависимости PM сервера
pip install -r pm/requirements.txt
```

### 1.3. Настройка переменных окружения

Для AI-анализа приоритетов (опционально, но рекомендуется):

```bash
export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"
export PM_USE_AI=true  # По умолчанию true
```

Другие параметры (опционально):

```bash
export PM_HOST="0.0.0.0"      # По умолчанию 0.0.0.0
export PM_PORT=8012           # По умолчанию 8012
export PM_NO_AUTH=true        # По умолчанию true (без auth)
export PM_DATA_DIR="./pm/data"  # По умолчанию ./pm/data
```

### 1.4. Проверка тестовых данных

Проверьте, что есть тестовые данные:

```bash
cat mcp-servers/pm/data/tasks.json | jq '.[] | {id, title, status}'
```

Должно показать список задач (task_001 до task_012).

## Шаг 2: Запуск всех MCP серверов

### 2.1. Использование START.sh (рекомендуется)

Самый простой способ - использовать `START.sh`, который запустит все три сервера:

```bash
cd mcp-servers
./START.sh
```

Вы должны увидеть:

```
🚀 MCP Servers - Multi-Server Start
====================================

✓ Python 3.x.x found
✓ Git x.x.x found

Starting MCP Servers:

1. Git MCP Server
   Repository: /path/to/your/repo
   URL: http://localhost:8010/sse

2. CRM MCP Server
   Data: /path/to/mcp-servers/crm/data
   URL: http://localhost:8011/sse

3. Project Management MCP Server
   Data: /path/to/mcp-servers/pm/data
   URL: http://localhost:8012/sse

[Git MCP] Starting on port 8010...
============================================================
Git MCP Server Starting
============================================================

[CRM MCP] Starting on port 8011...
============================================================
CRM MCP Server Starting
============================================================

[PM MCP] Starting on port 8012...
============================================================
Project Management MCP Server Starting
============================================================
Data directory: /path/to/pm/data
Host: 0.0.0.0
Port: 8012
Authentication: DISABLED
AI Analysis: ENABLED
============================================================

✓ Servers started!

Git MCP Server: http://localhost:8010 (PID: xxxx)
CRM MCP Server: http://localhost:8011 (PID: xxxx)
PM MCP Server:  http://localhost:8012 (PID: xxxx)
```

### 2.2. Запуск вручную (альтернатива)

Если нужно запустить только PM сервер:

```bash
cd mcp-servers
source venv/bin/activate
python -m pm.main --no-auth
```

Или с параметрами:

```bash
python -m pm.main --host 0.0.0.0 --port 8012 --no-auth
```

### 2.3. Проверка работы серверов

Проверьте каждый сервер:

```bash
# Git MCP Server
curl http://localhost:8010/

# CRM MCP Server
curl http://localhost:8011/

# PM MCP Server
curl http://localhost:8012/
```

Должны получить JSON с информацией о сервере.

## Шаг 3: Настройка AI Agent

### 3.1. Добавление PM MCP Server

Запустите AI Agent:

```bash
./gradlew :ai-agent:run
```

Перейдите в **Settings** → **MCP Servers** → **Add Server**:

- **Name:** PM Server (или Project Management)
- **URL:** `http://localhost:8012/sse`
- **Authentication:** (оставить пустым, если PM_NO_AUTH=true)

Нажмите **Add** и убедитесь, что статус сервера: ✅ **Connected**.

### 3.2. Проверка доступных инструментов

В списке инструментов должны появиться:

**Project Management Tools:**
- create_task
- list_tasks
- get_task
- update_task
- delete_task
- get_project_dashboard
- get_team_workload
- analyze_priorities
- suggest_priority

**Git Tools** (если Git MCP настроен):
- git_status, git_log, git_diff, и др.

**CRM Tools** (если CRM MCP настроен):
- get_user, get_user_tickets, search_tickets, и др.

### 3.3. Настройка RAG (опционально)

Для лучшего понимания контекста проекта:

```bash
# Индексировать документацию проекта
./gradlew :shared:runIndexing --args="index ./docs ./project-knowledge.json md"

# Индексировать day-24 документацию
./gradlew :shared:runIndexing --args="index ./day-24-docs ./day24-knowledge.json md"
```

В AI Agent Settings:
- **RAG Mode:** ON
- **Index Path:** `./project-knowledge.json`

## Шаг 4: Проверка работы

### 4.1. Тест команды /task help

В AI Agent отправьте:

```
/task help
```

Должно показать справку по команде /task со списком подкоманд.

### 4.2. Тест списка задач

```
/task list
```

Должно показать все задачи из `tasks.json` (12 задач).

### 4.3. Тест дашборда

```
/task status
```

Должно показать:
- Общую статистику (4 выполнено, 1 в работе, и т.д.)
- Распределение по статусам и приоритетам
- Метрики времени
- Загрузку команды
- Velocity

### 4.4. Тест AI-анализа приоритетов

**Важно:** Требует настроенные GigaChat credentials!

```
/task priorities
```

Должно показать:
- Общий анализ ситуации
- Рекомендации по приоритетам для конкретных задач
- Оптимальный порядок выполнения
- Риски и способы их снижения
- Инсайты

### 4.5. Тест создания задачи с AI

```
/task create Добавить поддержку темной темы в UI
```

Должно:
1. Вызвать `suggest_priority` для анализа
2. Получить рекомендации от AI
3. Создать задачу с предложенными параметрами
4. Показать результат

### 4.6. Тест деталей задачи

```
/task task_005
```

Должно показать полную информацию о задаче task_005.

## Шаг 5: Дополнительная настройка

### 5.1. Настройка логирования

Логи серверов сохраняются в `/tmp/`:

```bash
# Просмотр логов PM сервера
tail -f /tmp/pm-mcp.log

# Просмотр всех логов
tail -f /tmp/git-mcp.log /tmp/crm-mcp.log /tmp/pm-mcp.log
```

### 5.2. Добавление собственных задач

Отредактируйте `mcp-servers/pm/data/tasks.json`:

```json
{
  "id": "task_013",
  "project_id": "proj_001",
  "title": "Моя новая задача",
  "description": "Описание задачи",
  "status": "open",
  "priority": "medium",
  "assignee": "",
  "labels": [],
  "git_branch": "",
  "git_commits": [],
  "created_at": "2026-02-15T12:00:00Z",
  "updated_at": "2026-02-15T12:00:00Z",
  "due_date": "",
  "estimated_hours": 0,
  "spent_hours": 0
}
```

Перезапустите PM сервер или используйте API для динамического добавления.

### 5.3. Настройка проекта

Отредактируйте `mcp-servers/pm/data/projects.json`:

```json
{
  "id": "proj_001",
  "name": "Ваш проект",
  "description": "Описание",
  "team": ["dev1", "dev2"],
  ...
}
```

## Troubleshooting

### PM Server не запускается

**Проблема:** `ModuleNotFoundError: No module named 'pm'`

**Решение:**
```bash
cd mcp-servers
export PYTHONPATH="${PYTHONPATH}:$(pwd)"
python -m pm.main --no-auth
```

### AI-анализ не работает

**Проблема:** `AI analysis is disabled`

**Решение:**
1. Проверьте переменные окружения:
   ```bash
   echo $GIGACHAT_CLIENT_ID
   echo $GIGACHAT_CLIENT_SECRET
   ```
2. Установите `PM_USE_AI=true`
3. Перезапустите PM сервер

### Задачи не отображаются

**Проблема:** `No tasks found`

**Решение:**
1. Проверьте путь к данным:
   ```bash
   cat mcp-servers/pm/data/tasks.json
   ```
2. Убедитесь, что JSON валиден:
   ```bash
   cat mcp-servers/pm/data/tasks.json | jq .
   ```
3. Проверьте права доступа:
   ```bash
   ls -la mcp-servers/pm/data/
   ```

### MCP Server показывает "Error" в AI Agent

**Проблема:** Сервер не подключается

**Решение:**
1. Проверьте, что сервер запущен:
   ```bash
   curl http://localhost:8012/
   ```
2. Проверьте URL в настройках AI Agent
3. Проверьте логи сервера:
   ```bash
   tail -f /tmp/pm-mcp.log
   ```

### Команда /task не работает

**Проблема:** `Unknown command: /task`

**Решение:**
1. Убедитесь, что используете последнюю версию AI Agent
2. Проверьте, что изменения в CommandHandler применены
3. Пересоберите проект:
   ```bash
   ./gradlew clean build
   ./gradlew :ai-agent:run
   ```

## Полезные команды

### Проверка статуса серверов

```bash
# Проверка запущенных процессов
ps aux | grep "python -m.*mcp"

# Проверка портов
lsof -i :8010  # Git
lsof -i :8011  # CRM
lsof -i :8012  # PM

# Проверка доступности
curl http://localhost:8010/ && echo "Git OK"
curl http://localhost:8011/ && echo "CRM OK"
curl http://localhost:8012/ && echo "PM OK"
```

### Очистка и перезапуск

```bash
# Остановить все серверы
pkill -f "python -m.*mcp"

# Очистить логи
rm /tmp/*-mcp.log

# Перезапустить
cd mcp-servers
./START.sh
```

### Просмотр данных

```bash
# Список всех задач
cat mcp-servers/pm/data/tasks.json | jq '.[] | {id, title, status, priority}'

# Задачи по статусу
cat mcp-servers/pm/data/tasks.json | jq '.[] | select(.status == "open")'

# Задачи по приоритету
cat mcp-servers/pm/data/tasks.json | jq '.[] | select(.priority == "high")'

# Статистика
cat mcp-servers/pm/data/tasks.json | jq 'group_by(.status) | map({status: .[0].status, count: length})'
```

## Следующие шаги

После успешной настройки:

1. Изучите [TEST_SCENARIOS.md](TEST_SCENARIOS.md) для примеров использования
2. Прочитайте [DAY_24_README.md](DAY_24_README.md) для понимания возможностей
3. См. [../mcp-servers/pm/README.md](../mcp-servers/pm/README.md) для API документации
4. Экспериментируйте с различными командами /task

## Поддержка

При возникновении проблем:
1. Проверьте логи серверов в `/tmp/`
2. Убедитесь, что все зависимости установлены
3. Проверьте версии Python и Java
4. См. раздел Troubleshooting выше
