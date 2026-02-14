# AI Agent Improvement Plan

> Цель: превратить ai-agent в полноценного AI-агента уровня Claude Code.
> Создан: 2026-02-11
> Последнее обновление: 2026-02-11

---

## Статусы

- [ ] — не начато
- [~] — в процессе
- [x] — завершено
- [!] — заблокировано

---

## Phase 1 — Критические исправления (фундамент)

### 1.1 Настоящие сабагенты с изоляцией контекста
- **Статус:** [ ]
- **Файлы:** `AgentExecutor.kt`, `AgentRepository.kt`, `AgentRegistry.kt`, `TaskTool.kt`
- **Что сделать:**
  - [ ] `AgentExecutor.execute()` — создавать отдельный `AgentRepository` (или клонировать) с чистой `conversationHistory` для каждого сабагента
  - [ ] Передавать сабагенту только его system prompt + задачу, без истории родителя
  - [ ] Ограничить доступные tools по типу агента (Explore → read/glob/grep; Plan → read/glob/grep/write/ask_user_question)
  - [ ] Использовать поле `model` из `AgentDefinition` для маршрутизации на нужную модель
  - [ ] Реализовать `maxTurns` — лимит итераций tool-loop per agent
  - [ ] Результат сабагента возвращать как строку обратно в родительский контекст
- **Критерий готовности:** TaskTool вызывает сабагента с изолированным контекстом, ограниченными tools и лимитом итераций

### 1.2 Параллельный вызов инструментов
- **Статус:** [ ]
- **Файлы:** `AgentRepository.kt` (sendMessage loop), `GigaChatApiImpl.kt`
- **Что сделать:**
  - [ ] Проверить, поддерживает ли GigaChat API возврат нескольких function_call в одном ответе (или эмулировать через prompt engineering)
  - [ ] Если да — парсить массив function_calls из одного choice
  - [ ] Выполнять tool calls параллельно через `coroutineScope { async {} }` + `awaitAll()`
  - [ ] Собирать результаты и отправлять как batch of function messages
  - [ ] Если GigaChat не поддерживает — оставить последовательный вызов, но добавить в system prompt инструкцию «вызывай один инструмент за раз»
- **Критерий готовности:** При нескольких tool calls в одном ответе — параллельное выполнение; при одном — без изменений

### 1.3 Permission system для опасных операций
- **Статус:** [ ]
- **Файлы:** `AgentRepository.kt`, `BashTool.kt`, `WriteTool.kt`, `EditTool.kt`, новый `PermissionManager.kt`
- **Что сделать:**
  - [ ] Создать `PermissionManager` с классификацией tools: safe (read, glob, grep, task_list, task_get) vs dangerous (bash, write, edit, git push)
  - [ ] Перед выполнением dangerous tool — запрашивать подтверждение через `AskUserQuestionTool` callback или UI dialog
  - [ ] Whitelist для bash: `git status`, `git log`, `git diff`, `ls`, `pwd` — автоматически разрешены
  - [ ] Blacklist: `rm -rf`, `git push --force`, `git reset --hard` — всегда требуют подтверждения
  - [ ] В plan mode — разрешить только safe tools + write (для файла плана)
- **Критерий готовности:** Опасные операции требуют подтверждения; safe tools выполняются без задержки

---

## Phase 2 — Качество агентного loop

### 2.1 Intelligent context management
- **Статус:** [ ]
- **Файлы:** `AgentRepository.kt`, `AgentStore.kt`, `ChatHistoryRepository.kt`
- **Что сделать:**
  - [ ] Вместо обрезки по символам (`maxTotalChars = 50000`) — реализовать суммаризацию: когда контекст превышает порог, старые сообщения суммируются через отдельный вызов AI
  - [ ] При восстановлении сессии (`restoreHistory`) — сохранять и загружать TOOL_CALL/TOOL_RESULT сообщения (сейчас теряются)
  - [ ] Реализовать sliding window: последние N сообщений полностью + суммари предыдущих
  - [ ] Добавить token counting (приблизительный, 1 token ≈ 4 chars для русского ≈ 2-3 chars) вместо char counting
- **Критерий готовности:** Длинные сессии не теряют контекст; восстановленные сессии содержат tool history

### 2.2 Retry и error recovery
- **Статус:** [ ]
- **Файлы:** `AgentRepository.kt`, `McpRepositoryImpl.kt`
- **Что сделать:**
  - [ ] Exponential backoff при 429/500/502/503 от GigaChat API (retry 3 раза: 1s, 2s, 4s)
  - [ ] При ошибке tool execution — retry 1 раз, затем вернуть ошибку AI для принятия решения
  - [ ] При потере MCP соединения — автоматический reconnect + retry tool call
  - [ ] Таймаут для tool execution настраиваемый (сейчас 30s hardcoded)
- **Критерий готовности:** Transient errors не ломают агентный loop; AI получает информацию об ошибках и может адаптироваться

### 2.3 Streaming responses
- **Статус:** [ ]
- **Файлы:** `GigaChatApiImpl.kt`, `AgentRepository.kt`, `AgentStore.kt`, `AgentScreen.kt`
- **Что сделать:**
  - [ ] Проверить поддержку `stream: true` в GigaChat API
  - [ ] Реализовать SSE-парсинг потокового ответа
  - [ ] В `AgentRepository` — возвращать `Flow<AgentMessage>` вместо `List<AgentMessage>`
  - [ ] В `AgentStore` — инкрементальное обновление state при каждом chunk
  - [ ] В `AgentScreen` — отображение печатающегося текста
  - [ ] При появлении function_call в потоке — прерывать стриминг и переходить к execution
- **Критерий готовности:** Текст ответа появляется посимвольно/по-чанково в UI

---

## Phase 3 — Расширение возможностей

### 3.1 Background agents
- **Статус:** [ ]
- **Файлы:** `AgentExecutor.kt`, `TaskTool.kt`, `TaskRepository.kt`
- **Что сделать:**
  - [ ] `executeInBackground()` — запускать реальную корутину через `CoroutineScope.launch`
  - [ ] Писать output в `TaskRepository` с обновлением статуса (RUNNING → COMPLETED/FAILED)
  - [ ] `task_get` — возвращать текущий output + статус background задачи
  - [ ] Поддержка отмены через `task_update` со статусом CANCELLED
  - [ ] Лимит одновременных background агентов (3-5)
- **Критерий готовности:** Агент запускается в фоне, прогресс доступен через task_get, результат сохраняется

### 3.2 Рефакторинг и устранение дублирования
- **Статус:** [ ]
- **Файлы:** `AgentStore.kt`, `AgentRepository.kt`
- **Что сделать:**
  - [ ] Извлечь логику перенумерации источников (строки 174-215 и 392-427 в AgentStore.kt) в отдельный `SourceRenumberer` utility
  - [ ] Разбить `buildSystemPrompt()` на composable методы: `buildToolSection()`, `buildRagSection()`, `buildPlanModeSection()`, `buildCitationRules()`
  - [ ] Вынести magic numbers в constants/config: `MAX_ITERATIONS=10`, `MAX_TOTAL_CHARS=50000`, `MAX_MESSAGE_LENGTH=5000`
- **Критерий готовности:** Нет дублирования; system prompt строится из переиспользуемых блоков

### 3.3 Dynamic RAG через tools
- **Статус:** [ ]
- **Файлы:** новый `RagSearchTool.kt`, `RagIndexTool.kt`, `RagRepository.kt`
- **Что сделать:**
  - [ ] Создать `rag_search` tool — агент может сам искать по индексу в любой момент
  - [ ] Создать `rag_index` tool — агент может добавить файл/директорию в индекс
  - [ ] Auto-index при первом запуске: если `CLAUDE.md` найден, проиндексировать проект
  - [ ] Инкрементальное обновление индекса (добавление без полной переиндексации)
- **Критерий готовности:** Агент может динамически искать и индексировать документы через tool calls

### 3.4 Hooks system
- **Статус:** [ ]
- **Файлы:** новый `HookManager.kt`, `AgentRepository.kt`
- **Что сделать:**
  - [ ] Создать `HookManager` с типами хуков: `PreToolHook`, `PostToolHook`, `PreCommitHook`, `OnMessageHook`
  - [ ] Хуки определяются в конфиге (JSON/YAML файл в корне проекта)
  - [ ] Каждый хук — shell команда, которая получает context (tool name, args, result)
  - [ ] Результат хука может: allow, deny, modify args
  - [ ] Интеграция с lint, test runners, CI
- **Критерий готовности:** Пользователь может настроить хуки для автоматических проверок при вызове инструментов

---

## Зависимости между задачами

```
1.1 (Сабагенты) ← не зависит ни от чего
1.2 (Параллельные tools) ← не зависит ни от чего
1.3 (Permissions) ← не зависит ни от чего

2.1 (Context management) ← может начаться параллельно с Phase 1
2.2 (Retry/recovery) ← может начаться параллельно с Phase 1
2.3 (Streaming) ← после 2.1 (нужен Flow-based API)

3.1 (Background agents) ← после 1.1 (нужны изолированные агенты)
3.2 (Рефакторинг) ← после Phase 1 и 2 (чтобы не конфликтовать)
3.3 (Dynamic RAG) ← после 1.1 (RAG tools нужны в agent tool registry)
3.4 (Hooks) ← после 1.3 (hooks как расширение permission system)
```

---

## Журнал выполнения

| Дата | Задача | Действие | Результат |
|------|--------|----------|-----------|
| 2026-02-11 | — | Создан план | Анализ завершён, план сохранён |

---

## Как продолжить после рестарта

1. Открой этот файл: `IMPROVEMENT_PLAN.md`
2. Найди первую задачу со статусом `[ ]` или `[~]`
3. Прочитай секцию «Что сделать» и «Файлы»
4. Обнови статус на `[~]` перед началом работы
5. По завершении — обнови статус на `[x]` и добавь запись в «Журнал выполнения»
6. Перейди к следующей задаче

**Рекомендуемый порядок старта:** 1.1 → 1.2 → 1.3 → 2.1 → 2.2 → 3.1 → 3.2 → 2.3 → 3.3 → 3.4
