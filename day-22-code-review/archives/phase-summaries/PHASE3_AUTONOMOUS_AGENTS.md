# Phase 3: Autonomous Agents

## Обзор

Phase 3 добавляет систему **автономных специализированных агентов**, которые могут быть вызваны для выполнения сложных задач независимо. Это мощная возможность делегирования работы экспертам в своих областях.

## Реализованные возможности

### 1. Task Tool - Вызов специализированных агентов

```json
{
  "name": "task",
  "parameters": {
    "subagent_type": "Explore",
    "prompt": "Find all authentication-related files in the codebase",
    "description": "Finding auth files",
    "model": "haiku",  // optional: sonnet (default), opus, haiku
    "max_turns": 10,  // optional: max API round-trips
    "run_in_background": false,  // optional: background execution
    "resume": "agent_xxx"  // optional: resume from previous
  }
}
```

**Возможности:**
- Запуск 25 специализированных агентов
- Синхронный и фоновый режимы
- Resume mechanism (продолжение работы)
- Выбор модели (sonnet/opus/haiku)
- Ограничение turns для контроля

### 2. Agent Registry - 25 специализированных агентов

#### Exploration & Planning (2)

**Explore Agent** (`subagent_type: "Explore"`)
- Быстрое исследование кодовой базы
- Поиск файлов по паттернам
- Поиск кода по ключевым словам
- Ответы на вопросы о структуре кода
- Режимы: quick, medium, thorough

**Plan Agent** (`subagent_type: "Plan"`)
- Архитектурное планирование
- Пошаговые планы реализации
- Анализ критических файлов
- Рассмотрение trade-offs

#### General Purpose (1)

**General Purpose** (`subagent_type: "general-purpose"`)
- Сложные исследовательские вопросы
- Поиск кода
- Многошаговые задачи
- Автономное решение проблем

#### Language Specialists (5)

- **python-pro** - Python 3.11+, async, FastAPI, Django
- **kotlin-specialist** - Coroutines, Multiplatform, Android
- **typescript-pro** - Advanced types, full-stack TypeScript
- **java-architect** - Enterprise Java, Spring, microservices
- **rust-engineer** - Systems programming, memory safety

#### Backend & Infrastructure (3)

- **backend-developer** - API design, microservices
- **devops-engineer** - CI/CD, Docker, Kubernetes
- **database-optimizer** - Query optimization, indexes

#### Frontend & Mobile (3)

- **frontend-developer** - Modern JavaScript, React/Vue/Angular
- **react-specialist** - React 18+, hooks, server components
- **mobile-app-developer** - React Native, Flutter, native

#### Quality & Testing (4)

- **qa-expert** - Test strategy, quality metrics
- **test-automator** - Test frameworks, automation
- **code-reviewer** - Code quality, security, best practices
- **debugger** - Root cause analysis, debugging

#### Architecture & Design (3)

- **architect-reviewer** - Architecture validation
- **refactoring-specialist** - Safe refactoring, patterns
- **api-designer** - RESTful, GraphQL API design

#### Documentation & Content (2)

- **technical-writer** - Documentation, user guides
- **api-documenter** - API docs, OpenAPI/Swagger

## Примеры использования

### Пример 1: Исследование кодовой базы

```json
// User спрашивает: "Где обрабатываются ошибки клиента?"

// AI вызывает Explore agent
{
  "name": "task",
  "parameters": {
    "subagent_type": "Explore",
    "prompt": "Find where client errors are handled in the codebase. Look for error handling patterns, try-catch blocks, and error middleware.",
    "description": "Finding error handling",
    "model": "haiku"  // fast model for exploration
  }
}

// Explore agent возвращает:
// "Client errors are handled in src/services/process.ts:712
//  in the connectToServer function. There's also a global
//  error handler in src/middleware/errorHandler.ts."
```

### Пример 2: Планирование реализации

```json
// User: "Plan implementation of user authentication"

// AI вызывает Plan agent
{
  "name": "task",
  "parameters": {
    "subagent_type": "Plan",
    "prompt": "Design an implementation plan for user authentication with JWT tokens. Consider: token generation, refresh strategy, secure storage, middleware integration.",
    "description": "Planning JWT authentication"
  }
}

// Plan agent возвращает детальный план с шагами
```

### Пример 3: Code Review

```json
// User: "Review this authentication code for security issues"

{
  "name": "task",
  "parameters": {
    "subagent_type": "code-reviewer",
    "prompt": "Review the authentication implementation in src/auth/ for security vulnerabilities, best practices, and potential issues. Focus on: password storage, token security, session management.",
    "description": "Reviewing auth code"
  }
}
```

### Пример 4: Фоновое выполнение

```json
// Для долгих задач можно запустить в фоне
{
  "name": "task",
  "parameters": {
    "subagent_type": "test-automator",
    "prompt": "Write comprehensive unit tests for the authentication module",
    "description": "Writing auth tests",
    "run_in_background": true
  }
}

// Возвращает:
// "✓ Agent launched in background
//  Task ID: agent_1234567890_5678
//  Use task_get to check progress"

// Позже проверить:
{
  "name": "task_get",
  "parameters": {
    "taskId": "agent_1234567890_5678"
  }
}
```

### Пример 5: Resume механизм

```json
// Продолжить работу агента
{
  "name": "task",
  "parameters": {
    "subagent_type": "python-pro",
    "prompt": "Now add error handling to the functions",
    "description": "Adding error handling",
    "resume": "agent_1234567890_5678"  // ID предыдущего выполнения
  }
}
```

## Когда использовать Task tool

### ✅ ДА - используй Task tool для:

1. **Исследования кодовой базы**
   - "Where is feature X implemented?"
   - "Find all usages of class Y"
   - "How does module Z work?"

2. **Сложных вопросов**
   - Требуют research
   - Множество попыток поиска
   - Неочевидные ответы

3. **Специализированные задачи**
   - Язык-специфичные вопросы
   - Архитектурные решения
   - Code review
   - Написание тестов

4. **Планирование**
   - Разработка implementation plans
   - Анализ архитектурных trade-offs

### ❌ НЕТ - НЕ используй для:

1. **Простых операций**
   - Чтение известного файла → используй `read`
   - Поиск конкретного класса → используй `grep`
   - Задачи решаются в 1-2 tool calls

2. **Известные локации**
   - Знаешь путь к файлу → используй `read`
   - Конкретный паттерн → используй `grep`

## Архитектура

### Components

```
TaskTool ←→ AgentExecutor ←→ AgentRepository
    ↓            ↓                  ↓
AgentRegistry  AgentTask      AI API (GigaChat)
```

### Agent Execution Flow

```
1. Task tool invoked
         ↓
2. AgentRegistry validates agent type
         ↓
3. AgentExecutor creates specialized prompt
         ↓
4. AgentRepository executes with AI API
         ↓
5. Result returned to user
```

### Specialized Prompts

Каждый агент получает специализированный промпт:

```
You are a [Agent Name].

Description: [Agent description]

Your capabilities:
- Capability 1
- Capability 2
- ...

Task:
[User's prompt]

Provide a detailed, professional response using your specialized expertise.
```

## Режимы выполнения

### Синхронный (по умолчанию)

```json
{
  "run_in_background": false  // или не указывать
}
```

- Блокирует до завершения
- Возвращает полный результат сразу
- Подходит для большинства задач

### Фоновый

```json
{
  "run_in_background": true
}
```

- Возвращается сразу с task ID
- Агент работает в фоне
- Проверка прогресса через `task_get`
- Подходит для долгих задач

## Выбор модели

### haiku (Fast)
- Быстрые задачи
- Исследование кодовой базы
- Простые вопросы
- **Recommended для**: Explore agent

### sonnet (Balanced) - по умолчанию
- Большинство задач
- Хороший баланс скорости и качества
- **Recommended для**: большинства агентов

### opus (Best)
- Самые сложные задачи
- Критические решения
- Архитектурное планирование
- **Recommended для**: Plan agent, architect-reviewer

## Known Limitations

1. **Simplified execution** - агенты используют основной AI с specialized prompt (не отдельные инстансы)
2. **No parallel agents** - нельзя запустить несколько агентов параллельно
3. **Background not implemented** - фоновое выполнение пока возвращает placeholder
4. **Resume not implemented** - механизм resume пока возвращает placeholder
5. **No agent history** - история выполнения агентов не сохраняется
6. **No agent state** - агенты не сохраняют состояние между вызовами

## Roadmap

### Phase 3.1: Full Implementation
- [ ] Separate AI instances для каждого агента
- [ ] True background execution с coroutines
- [ ] Resume mechanism с сохранением контекста
- [ ] Agent history и логирование

### Phase 3.2: Advanced Features
- [ ] Parallel agent execution
- [ ] Agent communication (агенты могут общаться)
- [ ] Agent state persistence
- [ ] Agent memory (долгосрочная память)
- [ ] Custom agent definition через config

### Phase 3.3: UI Integration
- [ ] Agent task list в UI
- [ ] Real-time progress indicators
- [ ] Agent output streaming
- [ ] Agent history viewer
- [ ] Agent performance metrics

## Best Practices

### 1. Выбор правильного агента

```
Question: "How does auth work?"
→ Use: Explore agent (быстрое исследование)

Task: "Review security of auth code"
→ Use: code-reviewer (специализированный review)

Task: "Write Python code"
→ Use: python-pro (язык-специфичный эксперт)
```

### 2. Качественные промпты

**Bad:**
```json
{
  "prompt": "Fix auth"
}
```

**Good:**
```json
{
  "prompt": "Review the authentication implementation in src/auth/AuthService.kt. Check for: 1) Proper password hashing, 2) Secure token storage, 3) Session timeout handling, 4) Input validation. Provide specific recommendations with code examples."
}
```

### 3. Description field

**Bad:**
```json
{
  "description": "Do something"
}
```

**Good:**
```json
{
  "description": "Reviewing auth security"  // 3-5 words, clear
}
```

### 4. Выбор модели

```
Fast task (< 30s) → haiku
Normal task (30s - 2min) → sonnet (default)
Complex task (> 2min) → opus
```

## Integration с другими фазами

### С Task Management (Phase 2)

```json
// 1. Создать задачу
{"name": "task_create", "parameters": {"subject": "Review codebase"}}

// 2. Запустить агента
{"name": "task", "parameters": {
  "subagent_type": "code-reviewer",
  "prompt": "Review entire codebase"
}}

// 3. Обновить задачу
{"name": "task_update", "parameters": {
  "taskId": "task_xxx",
  "status": "completed"
}}
```

### С Planning Mode (Phase 2.5)

```json
// В plan mode можно использовать агентов для исследования:

// 1. Войти в plan mode
{"name": "enter_plan_mode"}

// 2. Использовать Explore agent
{"name": "task", "parameters": {
  "subagent_type": "Explore",
  "prompt": "Explore authentication architecture"
}}

// 3. Использовать Plan agent
{"name": "task", "parameters": {
  "subagent_type": "Plan",
  "prompt": "Design JWT implementation strategy"
}}

// 4. Выйти с планом
{"name": "exit_plan_mode"}
```

## Производительность

### Время выполнения (примерное)

| Agent Type | Model | Typical Time |
|------------|-------|--------------|
| Explore | haiku | 10-30s |
| general-purpose | sonnet | 30s-2min |
| Plan | opus | 1-3min |
| code-reviewer | sonnet | 30s-1min |
| python-pro | sonnet | 30s-2min |

### Optimization Tips

1. **Используй haiku для быстрых задач**
2. **Batch похожие запросы** вместо множества мелких
3. **Cache результаты** исследования
4. **Prefer file operations** (read/grep) для простых задач
5. **Background execution** для долгих задач

## License

Проект создан в рамках AI Advent Challenge with Love.
