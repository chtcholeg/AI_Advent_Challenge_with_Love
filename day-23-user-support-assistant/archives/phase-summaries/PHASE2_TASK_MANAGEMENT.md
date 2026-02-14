# Phase 2: Task Management & Planning

## Обзор

Phase 2 добавляет систему управления задачами и интерактивное взаимодействие с пользователем, позволяя AI Agent работать более организованно и эффективно над сложными задачами.

## Реализованные возможности

### 1. Task Management System

Полнофункциональная система управления задачами с персистентным хранением в SQLDelight.

#### TaskCreate - Создание задач

```json
{
  "name": "task_create",
  "parameters": {
    "subject": "Fix authentication bug in login flow",
    "description": "Detailed description with context and acceptance criteria",
    "activeForm": "Fixing authentication bug",  // optional, for spinner display
    "metadata": {  // optional
      "priority": "high",
      "component": "auth"
    }
  }
}
```

**Когда использовать:**
- ✅ Сложные задачи (3+ шага)
- ✅ Пользователь дал список задач
- ✅ Нужно отследить прогресс
- ❌ Простая одношаговая задача
- ❌ Тривиальные операции

#### TaskUpdate - Обновление задач

```json
{
  "name": "task_update",
  "parameters": {
    "taskId": "task_1234567890_5678",
    "status": "in_progress",  // pending | in_progress | completed | deleted
    "owner": "agent_name",  // optional: claim task
    "addBlockedBy": ["task_xxx"],  // optional: dependencies
    "addBlocks": ["task_yyy"],  // optional: tasks this blocks
    "metadata": {  // optional: merge/update metadata
      "notes": "Fixed main issue"
    }
  }
}
```

**Workflow статусов:**
```
pending → in_progress → completed
                ↓
            deleted (soft delete)
```

**Важные правила:**
- Отмечать `in_progress` **перед** началом работы
- Отмечать `completed` **только** когда полностью выполнено
- Если есть ошибки/блокеры → оставить `in_progress` + создать новую задачу для блокера
- Никогда не отмечать `completed` если:
  - Тесты падают
  - Реализация частичная
  - Есть нерешенные ошибки
  - Не найдены необходимые файлы/зависимости

#### TaskList - Список задач

```json
{
  "name": "task_list"
  // No parameters - lists all tasks
}
```

**Возвращает:**
```
Task List (3 tasks):

ID: task_1234567890_5678
  Subject: Fix authentication bug
  Status: IN_PROGRESS
  Owner: agent_main
  Active form: Fixing authentication bug

ID: task_1234567891_1234
  Subject: Add unit tests
  Status: PENDING
  Blocked by: task_1234567890_5678

ID: task_1234567892_9999
  Subject: Update documentation
  Status: COMPLETED
```

**Когда использовать:**
- После завершения задачи (проверить разблокированные)
- Найти следующую задачу для работы
- Проверить общий прогресс
- Найти доступные задачи (pending, no owner, not blocked)

**Приоритет:** Работать над задачами в порядке ID (меньший ID = выше приоритет)

#### TaskGet - Детали задачи

```json
{
  "name": "task_get",
  "parameters": {
    "taskId": "task_1234567890_5678"
  }
}
```

**Возвращает полную информацию:**
- Полное описание и контекст
- Зависимости (blocks/blockedBy)
- Метаданные
- Временные метки (created/updated/completed)

**Когда использовать:**
- Перед началом работы над задачей
- Нужен полный контекст
- Проверить зависимости перед стартом

### 2. User Interaction - AskUserQuestion

Интерактивные вопросы с множественным выбором для уточнения требований.

```json
{
  "name": "ask_user_question",
  "parameters": {
    "questions": [
      {
        "question": "Which authentication method should we use?",
        "header": "Auth method",  // max 12 chars for chip/tag
        "options": [
          {
            "label": "JWT (Recommended)",  // 1-5 words
            "description": "Stateless tokens, good for distributed systems"
          },
          {
            "label": "Session cookies",
            "description": "Server-side sessions, simpler but requires sticky sessions"
          },
          {
            "label": "OAuth 2.0",
            "description": "Third-party auth, good for SSO"
          }
        ],
        "multiSelect": false  // default false, set true for non-exclusive choices
      }
    ]
  }
}
```

**Возможности:**
- 1-4 вопроса за раз
- 2-4 варианта на вопрос
- Автоматический вариант "Other" для custom input
- Multi-select для невзаимоисключающих вариантов
- Рекомендации: добавить "(Recommended)" к первому варианту

**Когда использовать:**
- Неоднозначные требования
- Несколько вариантов реализации
- Нужно выбрать технологию/библиотеку
- Уточнить детали поведения

**Plan Mode Note:** Использовать для уточнения требований **ПЕРЕД** финализацией плана. НЕ спрашивать "План готов?" - для этого есть ExitPlanMode.

## Архитектура

### Database Schema

```sql
CREATE TABLE Task (
    id TEXT PRIMARY KEY,
    subject TEXT NOT NULL,
    description TEXT NOT NULL,
    activeForm TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    owner TEXT,
    blocks TEXT,      -- comma-separated task IDs
    blockedBy TEXT,   -- comma-separated task IDs
    metadata TEXT,    -- JSON string
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    completedAt INTEGER
);
```

### Domain Models

```kotlin
data class Task(
    val id: String,
    val subject: String,
    val description: String,
    val activeForm: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val owner: String? = null,
    val blocks: List<String> = emptyList(),
    val blockedBy: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)

enum class TaskStatus {
    PENDING, IN_PROGRESS, COMPLETED, DELETED
}

data class TaskSummary(
    val id: String,
    val subject: String,
    val status: TaskStatus,
    val owner: String?,
    val blockedBy: List<String>,
    val activeForm: String?
)
```

### Repository Layer

```
TaskRepository (interface)
    ↓
TaskRepositoryImpl
    ↓
SQLDelight (McpDatabase)
```

**Методы:**
- `getAllTasks()`: Flow<List<Task>>
- `getTaskById(id)`: Task?
- `getPendingTasks()`: List<Task>
- `getTasksByStatus(status)`: List<Task>
- `getTasksByOwner(owner)`: List<Task>
- `createTask(task)`
- `updateTask(task)`
- `deleteTask(id)` - soft delete
- `getTaskSummaries()`: List<TaskSummary>
- `clearAllTasks()`

### Tool Integration

```
LocalTool (interface)
    ↓
TaskCreateTool, TaskUpdateTool, TaskListTool, TaskGetTool, AskUserQuestionTool
    ↓
LocalToolsProvider (manages all tools)
    ↓
McpRepositoryImpl (unified interface)
    ↓
AgentRepository (AI orchestration)
```

## Примеры использования

### Пример 1: Создание и выполнение задачи

```kotlin
// 1. AI получает сложную задачу от пользователя
User: "Implement user authentication with JWT"

// 2. AI создает задачи
task_create({
  "subject": "Design JWT authentication flow",
  "description": "Define token structure, refresh strategy, and storage",
  "activeForm": "Designing JWT authentication flow"
})

task_create({
  "subject": "Implement JWT token generation",
  "description": "Create functions for generating access and refresh tokens",
  "activeForm": "Implementing JWT token generation",
  "metadata": {"blockedBy": "Design task"}
})

// 3. Проверить список задач
task_list()

// 4. Начать работу над первой задачей
task_update({
  "taskId": "task_xxx",
  "status": "in_progress",
  "owner": "agent_main"
})

// 5. Получить детали
task_get({"taskId": "task_xxx"})

// 6. Работа выполнена
task_update({
  "taskId": "task_xxx",
  "status": "completed"
})

// 7. Проверить разблокированные задачи
task_list()
```

### Пример 2: Уточнение требований

```kotlin
// AI не уверен в подходе
ask_user_question({
  "questions": [
    {
      "question": "Where should JWT tokens be stored?",
      "header": "Storage",
      "options": [
        {
          "label": "localStorage",
          "description": "Simple but vulnerable to XSS"
        },
        {
          "label": "httpOnly cookie (Recommended)",
          "description": "Secure from XSS, requires CSRF protection"
        },
        {
          "label": "In-memory only",
          "description": "Most secure but lost on refresh"
        }
      ]
    }
  ]
})
```

### Пример 3: Обработка блокеров

```kotlin
// Задача in_progress, но обнаружена проблема
task_update({
  "taskId": "task_main",
  "status": "in_progress"  // keep in_progress!
})

// Создать задачу для блокера
task_create({
  "subject": "Install missing dependency: jsonwebtoken",
  "description": "npm install jsonwebtoken --save",
  "activeForm": "Installing jsonwebtoken"
})

// Связать зависимости
task_update({
  "taskId": "task_main",
  "addBlockedBy": ["task_blocker"]
})

// Решить блокер
bash({"command": "npm install jsonwebtoken --save"})

task_update({
  "taskId": "task_blocker",
  "status": "completed"
})

// Вернуться к основной задаче
task_update({
  "taskId": "task_main",
  "status": "in_progress",
  "owner": "agent_main"
})
```

## System Prompt Updates

AI получает обновленный system prompt:

```
Доступные категории инструментов:
- Local Tools (file operations, search, bash commands, task management, user interaction)
- Git MCP Server (если подключен)

TASK MANAGEMENT:
- Используй task_create для сложных задач (3+ шага)
- Отмечай in_progress ПЕРЕД началом работы
- Отмечай completed ТОЛЬКО когда полностью выполнено
- При блокерах: оставь in_progress + создай новую задачу
- Используй task_list после завершения для проверки разблокированных задач

USER INTERACTION:
- Используй ask_user_question для уточнения требований
- НЕ спрашивай "План готов?" - используй ExitPlanMode
```

## TODO: Planning Mode (Phase 2.5)

Режим планирования пока не реализован, но структура готова:

### EnterPlanMode (TODO)
- Вход в режим планирования
- Исследование кодовой базы
- Разработка implementation plan
- Использование AskUserQuestion для уточнений

### ExitPlanMode (TODO)
- Выход из режима планирования
- Запрос approval у пользователя
- Сохранение плана
- Переход к реализации

## Интеграция с UI

### TaskList UI Component (TODO)

Отображение задач в UI:
- Список активных задач с прогрессом
- Фильтрация по статусу
- Отображение зависимостей (блокировки)
- Inline progress spinner для in_progress задач

### UserQuestion UI Component (TODO)

Интерактивные вопросы:
- Chips/Tags для headers
- Radio buttons (single select) или checkboxes (multi select)
- Automatic "Other" option с text input
- Валидация перед отправкой ответа

## Следующие шаги

### Phase 2.5: Planning Mode (Приоритет)
- [ ] EnterPlanMode tool
- [ ] ExitPlanMode tool
- [ ] Plan file management
- [ ] Approval workflow

### Phase 3: Autonomous Agents
- [ ] Task tool для вызова специализированных агентов
- [ ] Explore agent
- [ ] Plan agent
- [ ] Resume механизм

### UI Improvements
- [ ] TaskList UI component
- [ ] Task progress indicators
- [ ] UserQuestion dialog component
- [ ] Task dependency visualization

## Известные ограничения

1. **No UI integration yet** - вопросы и задачи пока не отображаются в UI
2. **No plan mode** - EnterPlanMode/ExitPlanMode еще не реализованы
3. **No task notifications** - нет уведомлений о разблокированных задачах
4. **No task filtering** - нельзя фильтровать задачи в UI
5. **Manual task claiming** - нужно вручную устанавливать owner

## Тестирование

### Создание задачи

```bash
./gradlew :ai-agent:run

# В AI Agent:
User: "Create a task to implement user login"
AI: [вызывает task_create]
User: "Show all tasks"
AI: [вызывает task_list]
```

### Проверка персистентности

```bash
# Создать задачи
User: "Create 3 tasks"

# Перезапустить приложение
Ctrl+C
./gradlew :ai-agent:run

# Проверить сохранение
User: "Show all tasks"
# Должны быть те же задачи
```

## License

Проект создан в рамках AI Advent Challenge with Love.
