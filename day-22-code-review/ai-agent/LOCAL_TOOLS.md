# Local Tools - Phase 1 Implementation

## Обзор

Локальные инструменты (Local Tools) - это встроенные возможности AI Agent, которые позволяют работать с файловой системой напрямую, без внешних MCP серверов. Это первый шаг к превращению ai-agent в Claude Code-подобный инструмент.

## Реализованные инструменты

### Phase 1: File Operations

### 1. **Read Tool** - Чтение файлов
```json
{
  "name": "read",
  "parameters": {
    "file_path": "absolute path to file",
    "offset": 0,      // optional: line number to start from
    "limit": 2000     // optional: max lines to read
  }
}
```

**Возможности:**
- Чтение файлов с нумерацией строк (формат cat -n)
- Поддержка offset/limit для больших файлов
- Автоматическое усечение длинных строк (>2000 символов)
- Работает с текстовыми файлами любых типов

### 2. **Write Tool** - Создание файлов
```json
{
  "name": "write",
  "parameters": {
    "file_path": "absolute path to file",
    "content": "file content"
  }
}
```

**Возможности:**
- Создание новых файлов
- Перезапись существующих файлов (с предупреждением)
- Автоматическое создание родительских директорий
- **Важно:** AI предпочитает использовать 'edit' для существующих файлов

### 3. **Edit Tool** - Редактирование файлов
```json
{
  "name": "edit",
  "parameters": {
    "file_path": "absolute path to file",
    "old_string": "exact text to find",
    "new_string": "replacement text",
    "replace_all": false  // optional: replace all occurrences
  }
}
```

**Возможности:**
- Точечная замена текста
- Проверка уникальности (защита от случайных изменений)
- Режим replace_all для переименования переменных
- Сохранение форматирования и отступов

### 4. **Glob Tool** - Поиск файлов по паттернам
```json
{
  "name": "glob",
  "parameters": {
    "pattern": "**/*.kt",  // glob pattern
    "path": "/optional/base/dir"
  }
}
```

**Возможности:**
- Быстрый поиск файлов по glob паттернам
- Поддержка wildcards (\*\*, \*, ?, [])
- Результаты отсортированы по времени изменения (новые первые)
- Рекурсивный поиск по директориям

### 5. **Grep Tool** - Поиск в содержимом
```json
{
  "name": "grep",
  "parameters": {
    "pattern": "regex pattern",
    "path": "/optional/base/dir",
    "glob": "*.kt",           // optional: filter files
    "type": "kt",             // optional: file type (kt, py, java, etc.)
    "case_insensitive": false,
    "output_mode": "files_with_matches",  // or "content", "count"
    "context_before": 0,
    "context_after": 0,
    "show_line_numbers": true,
    "head_limit": 0,
    "offset": 0
  }
}
```

**Возможности:**
- Мощный поиск на базе ripgrep (rg)
- Полная поддержка regex
- Фильтрация по типу файлов или glob паттернам
- Три режима вывода: файлы, содержимое, счетчики
- Context lines для лучшего понимания результатов

### 6. **Bash Tool** - Выполнение команд
```json
{
  "name": "bash",
  "parameters": {
    "command": "git status",
    "description": "Check git status",  // optional but recommended
    "timeout": 120000  // optional: max 600000ms
  }
}
```

**Возможности:**
- Выполнение bash команд (git, npm, docker, etc.)
- Настраиваемый timeout (по умолчанию 2 минуты, макс 10 минут)
- Автоматическое цитирование путей с пробелами
- **Важно:** Только для terminal операций, НЕ для file operations

### Phase 2: Task Management & User Interaction

### 7. **TaskCreate Tool** - Создание задач
```json
{
  "name": "task_create",
  "parameters": {
    "subject": "Fix bug in login",
    "description": "Detailed description with context",
    "activeForm": "Fixing bug in login",
    "metadata": {"priority": "high"}
  }
}
```

**Возможности:**
- Создание структурированных задач
- Автоматическая генерация ID
- Поддержка metadata для дополнительной информации
- Все задачи создаются со статусом PENDING

### 8. **TaskUpdate Tool** - Обновление задач
```json
{
  "name": "task_update",
  "parameters": {
    "taskId": "task_xxx",
    "status": "in_progress",
    "owner": "agent_name",
    "addBlockedBy": ["task_yyy"],
    "metadata": {"notes": "Progress update"}
  }
}
```

**Возможности:**
- Изменение статуса (pending → in_progress → completed)
- Назначение owner (claim task)
- Управление зависимостями (blocks/blockedBy)
- Обновление метаданных
- Soft delete (status = deleted)

### 9. **TaskList Tool** - Список задач
```json
{
  "name": "task_list"
}
```

**Возможности:**
- Показывает все активные задачи
- Summary view (id, subject, status, owner, blockedBy)
- Отсортировано по времени создания
- Исключает удаленные задачи

### 10. **TaskGet Tool** - Детали задачи
```json
{
  "name": "task_get",
  "parameters": {
    "taskId": "task_xxx"
  }
}
```

**Возможности:**
- Полная информация о задаче
- Описание и контекст
- Зависимости (blocks/blockedBy)
- Метаданные
- Временные метки

### 11. **AskUserQuestion Tool** - Интерактивные вопросы
```json
{
  "name": "ask_user_question",
  "parameters": {
    "questions": [{
      "question": "Which auth method?",
      "header": "Auth",
      "options": [
        {"label": "JWT", "description": "Stateless tokens"},
        {"label": "Sessions", "description": "Server-side"}
      ],
      "multiSelect": false
    }]
  }
}
```

**Возможности:**
- 1-4 вопроса за раз
- 2-4 варианта на вопрос
- Multi-select support
- Автоматический "Other" option
- Рекомендации через "(Recommended)"

### Phase 2.5: Planning Mode

### 12. **EnterPlanMode Tool** - Вход в режим планирования
```json
{
  "name": "enter_plan_mode",
  "parameters": {
    "task": "Implement JWT authentication"
  }
}
```

**Возможности:**
- Создает файл плана (.claude_plan_*.md)
- Шаблон плана с разделами (Analysis, Approach, Steps, Files, Considerations)
- Переводит в режим исследования
- Блокирует изменения кода (только планирование)
- В plan mode: read, glob, grep, ask_user_question, write (только план)
- Запрещено: edit, bash

**Когда использовать:**
- ✅ Новые фичи с несколькими вариантами
- ✅ Архитектурные решения
- ✅ Мультифайловые изменения (3+)
- ❌ Однострочные fixes
- ❌ Очевидные задачи

### 13. **ExitPlanMode Tool** - Выход с approval
```json
{
  "name": "exit_plan_mode",
  "parameters": {
    "allowedPrompts": [
      {"tool": "Bash", "prompt": "run tests"},
      {"tool": "Bash", "prompt": "install dependencies"}
    ]
  }
}
```

**Возможности:**
- Читает план из файла
- Показывает summary плана
- **Автоматически запрашивает approval** у пользователя
- Указывает allowedPrompts для реализации
- НЕ спрашивать "План готов?" - это делается автоматически

**AllowedPrompts:**
- Семантические описания действий (не конкретные команды)
- Примеры: "run tests", "install dependencies", "start server"

## Архитектура

### Структура файлов
```
ai-agent/src/commonMain/kotlin/
├── domain/tool/
│   └── LocalTool.kt              # Base interface
├── data/tool/
│   ├── LocalToolsProvider.kt     # Main provider
│   ├── ReadTool.kt
│   ├── WriteTool.kt
│   ├── EditTool.kt
│   ├── GlobTool.kt
│   ├── GrepTool.kt
│   └── BashTool.kt
└── util/
    └── FileSystem.kt             # Expect/actual platform abstraction

ai-agent/src/desktopMain/kotlin/
└── util/
    └── FileSystem.desktop.kt     # Desktop implementation

ai-agent/src/androidMain/kotlin/
└── util/
    └── FileSystem.android.kt     # Android stub (TODO)
```

### Интеграция с MCP

Локальные инструменты интегрированы в существующую MCP архитектуру:

1. **LocalToolsProvider** регистрирует инструменты с `serverId = "local"`
2. **McpRepositoryImpl** объединяет локальные и MCP инструменты в `getAllTools()`
3. **McpRepositoryImpl.executeTool()** проверяет локальные инструменты перед MCP
4. **AgentRepository** включает "Local Tools" в system prompt
5. **Koin DI** автоматически внедряет FileSystem и LocalToolsProvider

### System Prompt

AI получает информацию о локальных инструментах в system prompt:

```
КРИТИЧЕСКИЕ ПРАВИЛА ВЫБОРА ИНСТРУМЕНТОВ:
- Для чтения файлов ВСЕГДА используй 'read', НЕ 'bash' с cat/head/tail
- Для записи файлов ВСЕГДА используй 'write', НЕ 'bash' с echo/cat
- Для редактирования файлов ВСЕГДА используй 'edit', НЕ 'bash' с sed/awk
- Для поиска файлов ВСЕГДА используй 'glob', НЕ 'bash' с find/ls
- Для поиска в содержимом ВСЕГДА используй 'grep', НЕ 'bash' с grep/rg
- 'bash' только для git, npm, docker и других terminal операций

Доступные категории инструментов:
- Local Tools (file operations, search, bash commands)
- Git MCP Server (если подключен)
```

## Использование

### Пример 1: Чтение и редактирование файла

```kotlin
// AI вызывает инструменты последовательно:

// 1. Прочитать файл
read({
  "file_path": "/path/to/file.kt"
})

// 2. Отредактировать
edit({
  "file_path": "/path/to/file.kt",
  "old_string": "fun oldName() {",
  "new_string": "fun newName() {"
})
```

### Пример 2: Поиск и анализ

```kotlin
// 1. Найти все Kotlin файлы
glob({
  "pattern": "**/*.kt"
})

// 2. Найти определение класса
grep({
  "pattern": "class MyClass",
  "type": "kt",
  "output_mode": "files_with_matches"
})

// 3. Прочитать найденный файл
read({
  "file_path": "/result/from/grep.kt"
})
```

### Пример 3: Git workflow

```kotlin
// 1. Проверить статус
bash({
  "command": "git status",
  "description": "Check git status"
})

// 2. Коммит изменений
bash({
  "command": "git add . && git commit -m 'Update files'",
  "description": "Stage and commit changes"
})
```

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| Desktop (JVM) | ✅ Полная поддержка | Java File API + Process |
| Android | ⚠️ Stub | Требует доработки из-за scoped storage |

### Desktop Implementation

Использует:
- `java.io.File` для file operations
- `java.nio.file.Files` для glob
- `ProcessBuilder` для bash команд
- `rg` (ripgrep) для grep (должен быть установлен)

### Android TODO

Android требует специальной обработки из-за ограничений scoped storage:
- Использовать `DocumentFile` для доступа к файлам
- Ограничить доступ app-specific directories
- Bash команды не поддерживаются

## Следующие шаги (Phase 2 и далее)

### Phase 2: Task Management & Planning
- [ ] TaskCreate/TaskUpdate/TaskList/TaskGet
- [ ] EnterPlanMode/ExitPlanMode
- [ ] AskUserQuestion с вариантами ответов

### Phase 3: Autonomous Agents
- [ ] Task Tool для вызова специализированных агентов
- [ ] Explore agent для исследования кодовой базы
- [ ] Plan agent для планирования реализации

### Phase 4: Advanced Features
- [ ] WebFetch/WebSearch
- [ ] NotebookEdit для Jupyter notebooks
- [ ] Skill system

### Улучшения Local Tools
- [ ] PDF reading support (через PDFBox)
- [ ] Image viewing (base64 encoding)
- [ ] Multiline regex в grep
- [ ] Background bash execution
- [ ] Android platform implementation

## Требования

### Desktop
- JDK 17+
- `rg` (ripgrep) установлен в PATH для grep tool
  ```bash
  # macOS
  brew install ripgrep

  # Linux
  apt install ripgrep

  # Windows
  choco install ripgrep
  ```

### Запуск

```bash
# Desktop
./gradlew :ai-agent:run

# Сборка
./gradlew :ai-agent:build
```

## Известные ограничения

1. **Grep требует ripgrep** - если rg не установлен, grep tool не работает
2. **Android stub** - Android версия требует реализации
3. **No file watching** - изменения файлов не отслеживаются автоматически
4. **Working directory** - bash команды выполняются в текущей директории gradle
5. **No sandboxing** - все file operations выполняются без ограничений (будьте осторожны!)

## Безопасность

⚠️ **ВАЖНО:** Локальные инструменты выполняются с полными правами приложения:
- Могут читать/писать любые доступные файлы
- Могут выполнять произвольные bash команды
- Нет песочницы (sandbox)

Для production использования рекомендуется:
- Добавить whitelist/blacklist путей
- Ограничить доступные bash команды
- Добавить confirmation dialog для опасных операций
- Логировать все file operations

## License

Проект создан в рамках AI Advent Challenge with Love.
