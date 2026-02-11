# Phase 2.5: Planning Mode

## Обзор

Phase 2.5 добавляет полноценный **режим планирования** (Planning Mode), позволяющий AI Agent разрабатывать детальные планы реализации перед началом кодирования. Это критически важная функция для сложных задач, требующих архитектурных решений.

## Реализованные возможности

### 1. EnterPlanMode - Вход в режим планирования

```json
{
  "name": "enter_plan_mode",
  "parameters": {
    "task": "Implement JWT authentication with refresh tokens"
  }
}
```

**Что происходит при входе:**

1. Создается файл плана `.claude_plan_<timestamp>.md` в текущей директории
2. Файл заполняется шаблоном плана с разделами:
   - Analysis (анализ задачи и кодовой базы)
   - Approach (подход к реализации)
   - Steps (пошаговый план)
   - Files to Modify (список файлов)
   - Considerations (важные соображения)
3. AI переходит в режим планирования
4. Состояние сохраняется в PlanModeRepository

**Когда использовать:**

✅ **ДА - используй enter_plan_mode для:**
- Новых фич с несколькими вариантами реализации
- Изменений кода, влияющих на существующую архитектуру
- Архитектурных решений (выбор паттернов, технологий)
- Мультифайловых изменений (3+ файлов)
- Неясных требований, требующих исследования
- Задач, где важны предпочтения пользователя

❌ **НЕТ - НЕ используй для:**
- Однострочных исправлений (опечатки, очевидные баги)
- Добавления одной функции с четкими требованиями
- Пользователь дал очень конкретные инструкции
- Чистое исследование/exploration (используй grep/read)

**В режиме планирования МОЖНО:**
- ✅ read, glob, grep - исследование кодовой базы
- ✅ ask_user_question - уточнение подхода
- ✅ write - ТОЛЬКО для обновления файла плана
- ✅ task_create, task_list, task_get, task_update - управление задачами

**В режиме планирования НЕЛЬЗЯ:**
- ❌ edit - редактирование кода
- ❌ bash - выполнение команд
- ❌ write для других файлов, кроме плана

### 2. ExitPlanMode - Выход из режима с approval

```json
{
  "name": "exit_plan_mode",
  "parameters": {
    "allowedPrompts": [
      {
        "tool": "Bash",
        "prompt": "run tests"
      },
      {
        "tool": "Bash",
        "prompt": "install dependencies"
      }
    ]
  }
}
```

**Что происходит при выходе:**

1. Читается содержимое файла плана
2. Показывается краткое содержание плана (первые 20 строк)
3. Отображается список allowedPrompts (разрешенных действий)
4. **Запрашивается одобрение пользователя** (автоматически)
5. Состояние переходит в "awaiting_approval"
6. После одобрения AI может приступить к реализации

**Allowed Prompts - Разрешенные действия:**

Это **семантические описания** категорий действий, а не конкретные команды:
- "run tests" - запуск тестов
- "install dependencies" - установка зависимостей
- "start dev server" - запуск dev сервера
- "build project" - сборка проекта

**Важные правила:**

- ⚠️ НЕ спрашивай пользователя "План готов?" или "Продолжить?"
- ✓ exit_plan_mode **автоматически** запрашивает approval
- ✓ Используй exit_plan_mode когда план полностью готов
- ✓ Если есть нерешенные вопросы, используй ask_user_question **ПЕРЕД** exit_plan_mode

### 3. Plan File Management

**Структура файла плана:**

```markdown
# Implementation Plan

**Task:** [Original task description]

**Created:** [Timestamp]

---

## Analysis

[Analyze the task and current codebase state]
- What exists currently
- What needs to be changed
- Key dependencies and integrations

## Approach

[Describe the overall implementation strategy]
- Why this approach was chosen
- Alternatives considered
- Trade-offs and rationale

## Steps

[Detailed step-by-step implementation plan]

1. Step 1: Description
   - Substep 1.1
   - Substep 1.2
2. Step 2: Description
3. Step 3: Description

## Files to Modify

[List all files that will be changed]

- `path/to/file1.kt` - What changes
- `path/to/file2.kt` - What changes
- `path/to/file3.kt` - What changes

## Considerations

[Important considerations, risks, trade-offs]

- Security considerations
- Performance impact
- Breaking changes
- Migration strategy
```

**AI должен:**
1. Тщательно исследовать кодовую базу (read, glob, grep)
2. Понять существующие паттерны и архитектуру
3. Написать детальный план в файл плана
4. Обновлять план по мере исследования
5. Использовать ask_user_question для уточнений
6. Вызвать exit_plan_mode когда готов

### 4. Plan Mode State Management

**PlanModeState:**

```kotlin
data class PlanModeState(
    val isActive: Boolean = false,
    val planFilePath: String? = null,
    val originalTask: String? = null,
    val enteredAt: Long? = null,
    val allowedPrompts: List<AllowedPrompt> = emptyList()
)
```

**Lifecycle:**

```
Normal Mode → enter_plan_mode → Planning Mode
                                      ↓
                              [explore, design, write]
                                      ↓
                              exit_plan_mode
                                      ↓
                              Awaiting Approval
                                      ↓
                              [user approves]
                                      ↓
                              Implementation Mode
```

## Архитектура

### Components

```
EnterPlanModeTool ←→ PlanModeRepository ←→ FileSystem
ExitPlanModeTool  ↗
                            ↓
                    PlanModeState (StateFlow)
                            ↓
                    AgentRepository (system prompt)
```

### Files Created

**Domain Models (1):**
- `domain/model/PlanMode.kt` - PlanModeState, AllowedPrompt, PlanApproval

**Repository (1):**
- `data/repository/PlanModeRepository.kt` - управление состоянием

**Tools (2):**
- `data/tool/EnterPlanModeTool.kt`
- `data/tool/ExitPlanModeTool.kt`

**Updated Files (3):**
- `data/tool/LocalToolsProvider.kt` - добавлены новые инструменты
- `data/repository/AgentRepository.kt` - обновлен system prompt
- `di/Koin.kt` - зарегистрирован PlanModeRepository

## System Prompt Updates

### Normal Mode Prompt:

```
PLANNING MODE:
- Use enter_plan_mode for non-trivial implementation tasks
- Prefer planning for: new features, architectural decisions, multi-file changes
- In plan mode: explore, design, write plan, then exit_plan_mode
- Exit plan mode requests user approval automatically
```

### Plan Mode Prompt:

```
🎯 PLAN MODE ACTIVE:
- You are in PLANNING MODE - designing implementation, NOT implementing
- ✓ ALLOWED: read, glob, grep, ask_user_question, write (for plan file only)
- ✗ FORBIDDEN: edit, bash (no code changes or commands in plan mode)
- When plan is complete, use exit_plan_mode to request user approval
- Do NOT ask "Is the plan ready?" - exit_plan_mode handles approval
```

## Примеры использования

### Пример 1: Полный workflow планирования

```
User: "Implement JWT authentication"

AI: [анализирует задачу - это сложная задача, требует планирования]

1. enter_plan_mode({"task": "Implement JWT authentication"})
   → ✓ Entered planning mode
   → Plan file: .claude_plan_1234567890.md

2. glob({"pattern": "**/*auth*.kt"})
   → Shows existing auth-related files

3. read({"file_path": "src/auth/AuthService.kt"})
   → Understands current auth system

4. grep({"pattern": "authentication", "type": "kt"})
   → Finds all auth references

5. ask_user_question({
     "questions": [{
       "question": "Where should JWT tokens be stored?",
       "header": "Storage",
       "options": [
         {"label": "httpOnly cookie", "description": "Secure from XSS"},
         {"label": "localStorage", "description": "Simple but vulnerable"},
         {"label": "memory only", "description": "Lost on refresh"}
       ]
     }]
   })
   → User chooses: "httpOnly cookie"

6. write({
     "file_path": ".claude_plan_1234567890.md",
     "content": "[detailed plan based on exploration]"
   })

7. exit_plan_mode({
     "allowedPrompts": [
       {"tool": "Bash", "prompt": "install dependencies"},
       {"tool": "Bash", "prompt": "run tests"}
     ]
   })
   → Shows plan summary
   → ⏳ Waiting for user approval...

User: [reviews plan] "Looks good, approved"

AI: 8. [proceeds with implementation using edit, write, bash]
```

### Пример 2: Простая задача - планирование не нужно

```
User: "Fix typo in README"

AI: [анализирует - это простая задача, планирование избыточно]

1. read({"file_path": "README.md"})
2. edit({
     "file_path": "README.md",
     "old_string": "authentification",
     "new_string": "authentication"
   })

Done - no planning needed.
```

### Пример 3: Уточнение в процессе планирования

```
AI: [in plan mode, exploring codebase]

1. read({"file_path": "src/api/UserApi.kt"})
   → Finds two possible integration points

2. ask_user_question({
     "questions": [{
       "question": "Should auth be middleware or service layer?",
       "header": "Arch",
       "options": [
         {"label": "Middleware (Recommended)", "description": "Intercepts all requests"},
         {"label": "Service layer", "description": "Manual in each endpoint"}
       ]
     }]
   })

3. [updates plan based on answer]

4. exit_plan_mode({...})
```

## UI Integration (TODO)

### Plan Mode Indicator

```
┌─────────────────────────────────────┐
│ 🎯 PLAN MODE                        │
│ Task: Implement JWT authentication  │
│ Plan file: .claude_plan_xxx.md      │
└─────────────────────────────────────┘
```

### Plan Approval Dialog (TODO)

```
┌────────────── Plan Review ──────────────┐
│                                          │
│ # Implementation Plan                    │
│                                          │
│ ## Analysis                              │
│ [plan content]                           │
│                                          │
│ ## Steps                                 │
│ 1. ...                                   │
│ 2. ...                                   │
│                                          │
│ ┌──────────────────────────────────┐    │
│ │ Allowed Actions:                 │    │
│ │ • Bash: run tests                │    │
│ │ • Bash: install dependencies     │    │
│ └──────────────────────────────────┘    │
│                                          │
│  [Approve]  [Request Changes]  [Cancel] │
└──────────────────────────────────────────┘
```

## Преимущества Planning Mode

1. **Снижение ошибок** - тщательное планирование перед кодированием
2. **Лучшая коммуникация** - пользователь видит план перед реализацией
3. **Архитектурная целостность** - продуманные решения вместо хаотичных изменений
4. **Экономия времени** - исправления в плане быстрее, чем в коде
5. **Документация** - план остается как документация изменений
6. **Approval workflow** - контроль пользователя над значительными изменениями

## Известные ограничения

1. **No UI integration** - план пока не отображается в красивом UI
2. **Manual approval** - пользователь должен явно одобрить план
3. **No plan templates** - нет готовых шаблонов для разных типов задач
4. **No plan versioning** - нельзя откатиться к предыдущей версии плана
5. **No collaborative editing** - план редактирует только AI

## Следующие шаги

### Phase 3: Autonomous Agents
- [ ] Task tool (вызов специализированных агентов)
- [ ] Explore agent (в режиме планирования)
- [ ] Plan agent (специализированный агент для планирования)
- [ ] Resume mechanism

### UI Enhancements
- [ ] Plan Mode indicator в UI
- [ ] Plan Approval dialog
- [ ] Diff view для изменений плана
- [ ] Plan templates selector
- [ ] Collaborative plan editing

### Planning Improvements
- [ ] Plan templates для разных типов задач
- [ ] Plan validation (completeness check)
- [ ] Plan cost estimation
- [ ] Plan versioning and history
- [ ] Auto-save plan drafts

## Тестирование

### Базовый workflow:

```bash
./gradlew :ai-agent:run

# В AI Agent:
User: "Implement user authentication system"

AI: [enters plan mode]
    [explores codebase]
    [writes plan]
    [exits plan mode]

User: "Approved"

AI: [implements based on plan]
```

### Проверка состояния:

```kotlin
// В коде можно проверить состояние:
planModeRepository.planModeState.value.isActive  // true/false
planModeRepository.planModeState.value.planFilePath  // path to plan
```

## Отличия от Claude Code

Наша реализация имеет некоторые отличия:

1. **Explicit plan file** - план хранится в .md файле
2. **AllowedPrompts** - семантические разрешения для действий
3. **StateFlow** - реактивное состояние планирования
4. **Template system** - автоматический шаблон плана

## License

Проект создан в рамках AI Advent Challenge with Love.
