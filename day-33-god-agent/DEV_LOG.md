# God Agent — Лог разработки

## Статус: IN PROGRESS

## Архитектурные решения

| Решение | Выбор | Причина |
|---|---|---|
| ReAct механизм | Prompt-engineering JSON | Универсально, работает с GigaChat и Ollama |
| Инструменты | Прямой Kotlin (ProcessBuilder, HTTP) | Надёжнее MCP протокола для одного дня |
| История | JSON файлы (как day-32) | Уже работает, не нужен SQLDelight |
| RAG векторное хранилище | SQLite через sqlite-jdbc | Проще чем SQLDelight для desktop |
| LLM провайдер | GigaChat или Ollama (настройки) | Как в day-32 |
| База проекта | day-32 (voice agent) | Есть голос, Koin, Ktor |

## ReAct протокол (промпт-инжиниринг)

LLM видит системный промпт со списком инструментов и должна отвечать ТОЛЬКО JSON:

Вызов инструмента:
```json
{"tool": "git_log", "args": {"n": 5}}
```

Финальный ответ:
```json
{"done": true, "answer": "...текст ответа..."}
```

## Структура пакета: ru.chtcholeg.godagent

```
src/main/kotlin/ru/chtcholeg/godagent/
├── Main.kt
├── App.kt
├── data/
│   ├── api/
│   │   ├── ApiModels.kt
│   │   ├── GigaChatApi.kt
│   │   └── OllamaApi.kt
│   ├── audio/
│   │   ├── AudioRecorder.kt
│   │   └── VoskSpeechRecognitionService.kt
│   ├── tools/
│   │   ├── AgentTool.kt          (interface + AgentStep sealed class)
│   │   ├── ToolExecutor.kt       (реестр + статусы + dispatch)
│   │   ├── GitTool.kt            (ProcessBuilder → git commands)
│   │   ├── GitHubTool.kt         (GitHub REST API)
│   │   ├── TelegramTool.kt       (Telegram Bot API)
│   │   ├── FileOpsTool.kt        (File I/O)
│   │   └── UtilityTools.kt       (weather, currency, time)
│   ├── rag/
│   │   ├── DocumentIndexer.kt    (chunk + embed)
│   │   ├── VectorStore.kt        (SQLite + cosine similarity)
│   │   └── RagTool.kt            (AgentTool обёртка)
│   └── repository/
│       ├── ChatRepository.kt     (ReAct loop)
│       ├── SessionRepository.kt
│       └── SettingsRepository.kt
├── di/AppModule.kt
├── domain/model/
│   ├── AppSettings.kt            (расширен: gitRepoPath, ragFolder, etc.)
│   ├── AgentStep.kt              (ToolCall, ToolResult, FinalAnswer, Error)
│   ├── ChatMessage.kt            (добавлены: THINKING, TOOL_CALL, TOOL_RESULT роли)
│   ├── ChatSession.kt
│   └── ModelInfo.kt
└── presentation/
    ├── chat/
    │   ├── ChatIntent.kt         (добавлен: StopAgent)
    │   ├── ChatScreen.kt
    │   ├── ChatState.kt          (добавлены: agentSteps, isAgentRunning)
    │   ├── ChatStore.kt
    │   └── ThinkingBlock.kt      (новый)
    ├── components/
    │   ├── MessageInput.kt
    │   ├── MessageItem.kt        (поддержка tool_call/tool_result сообщений)
    │   └── MessageList.kt
    ├── settings/SettingsScreen.kt (расширен: RAG, Git, MCP статусы)
    └── theme/Theme.kt
```

## Зависимости (добавляем к day-32)

```kotlin
// SQLite для RAG vector store
implementation("org.xerial:sqlite-jdbc:3.45.1.0")

// Kotlinx Coroutines дополнения
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")
```

## Выполненные шаги

- [x] Создан SPEC.md (интервью с пользователем)
- [x] Создан DEV_LOG.md
- [x] Task 1: Скопирован day-32, настроен build.gradle.kts (добавлен sqlite-jdbc)
- [x] Task 2: domain/model/* — AppSettings, AgentStep, ChatMessage, ChatSession, ModelInfo
- [x] Task 3: data/tools/* — AgentTool, ToolExecutor, Git, GitHub, Telegram, FileOps, Utility (weather/currency/time)
- [x] Task 4: data/rag/* — DocumentIndexer, VectorStore (SQLite), RagTool
- [x] Task 5: data/repository/ChatRepository — полный ReAct loop через prompt-engineering
- [x] Task 6: presentation/* — ThinkingBlock, ChatStore (с StopAgent), Settings (расширенный)
- [x] Компиляция: BUILD SUCCESSFUL (только warnings о Json{})
- [x] Запуск: приложение запустилось (PID активен)

## СТАТУС: ЗАВЕРШЕНО ✅

## Итог — что реализовано

35 Kotlin-файлов, 0 ошибок компиляции.

### Инструменты (13 штук)
- git_log, git_diff, git_status, git_branches (ProcessBuilder → git)
- github_list_prs, github_list_issues (GitHub REST API)
- telegram_send (Telegram Bot API)
- file_read, file_write, file_list (java.io.File)
- get_time (ZonedDateTime)
- get_weather (Open-Meteo, бесплатно без ключа)
- get_currency (Frankfurter, бесплатно без ключа)
- rag_search (DocumentIndexer → VectorStore)

### ReAct Loop
- Prompt-engineering: LLM отвечает только JSON
- {"tool": "name", "args": {...}} или {"done": true, "answer": "..."}
- Настраиваемое max iterations
- Компрессия истории при > ~16K символов

### UI
- Thinking Block: раскрывающийся серый блок с шагами в реальном времени
- Stop button (красная кнопка)
- Settings: профиль, LLM, RAG, Git, Telegram, GitHub, Voice, статусы инструментов

## Если нужно продолжить с этого места

1. Открыть проект в IntelliJ IDEA / Android Studio
2. Запустить: `./gradlew run`
3. В Settings настроить: GigaChat credentials (или Ollama URL), Git repo path
4. Для тестирования ReAct: спросить "какой статус git репозитория?" или "который час?"
- [ ] Task 7: AppModule.kt, Main.kt, App.kt

## Известные ограничения (для текущего дня)

- GitHub API rate limit — работает без токена для публичных реп
- Для RAG нужна папка с .txt/.md файлами
- Vosk модель скачивается автоматически при первом запуске (~50MB)
- Ollama embeddings требует запущенной Ollama с моделью nomic-embed-text

## Конфигурация (Settings)

Файл: `~/.god-agent/settings.json`
- selectedModelId, gigachatClientId/Secret (как раньше)
- ollamaBaseUrl (default: http://localhost:11434)
- gitRepoPath (default: текущая директория)
- ragFolderPath (default: null)
- telegramBotToken, telegramChatId
- githubToken (optional)
- maxReactIterations (default: 10)
- voiceKeywords (как раньше)

## Конфиг Tools

Папка: `~/.god-agent/`
Настройки MCP серверов — встроены в AppSettings, не отдельный файл.
