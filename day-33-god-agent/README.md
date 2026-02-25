# Day 33 — God Agent: персональный AI-сотрудник

Kotlin Compose Desktop-приложение «God Agent» — персональный AI-ассистент, объединяющий голосовой ввод (Vosk), RAG, 13 встроенных инструментов (Git, GitHub, Telegram, FileOps, Weather, Currency, Time) и персонализацию в единый ReAct-агент с prompt-engineering маршрутизацией.

## Что добавлено по сравнению с предыдущим днём

| Функция | Описание |
|---|---|
| **ReAct Loop** | LLM итеративно вызывает инструменты: думает → действует → смотрит результат → думает снова; настраиваемый лимит итераций |
| **13 инструментов** | git_log/diff/status/branches, github_list_prs/issues, telegram_send, file_read/write/list, get_time, get_weather, get_currency |
| **RAG** | DocumentIndexer (чанкинг .md/.txt) + VectorStore (SQLite + cosine similarity) + инструмент rag_search |
| **Thinking Block** | Раскрывающийся серый блок с шагами агента в реальном времени |
| **Stop Button** | Красная кнопка отмены — cancellation coroutine прерывает агента в любой момент |
| **Prompt-engineering JSON** | LLM отвечает строго JSON: `{"tool": "name", "args": {...}}` или `{"done": true, "answer": "..."}` |
| **Компрессия истории** | При накоплении > ~16K символов старые сообщения суммаризируются одним LLM-вызовом |
| **Расширенные Settings** | Секции: RAG (папка + Re-index), Git (путь к репо), Telegram/GitHub токены, статусы инструментов, max iterations |

## Как работает ReAct агент

```
[Голос (Vosk) / Текст]
         ↓
[Системный промпт: профиль + список инструментов]
         ↓
[LLM → JSON ответ]
         ↓
  ┌──────────────────────┐
  │ {"done": true}       │  {"tool": "name", "args": {...}}
  │ → Финальный ответ    │  → Вызов инструмента → результат → следующая итерация
  └──────────────────────┘
```

### ReAct Loop (упрощённо)

```
while (iterations < maxIterations) {
    val response = llm.chat(history + systemPrompt)
    if (response.isDone) → финальный ответ, выход
    val result = executeTool(response.toolName, response.args)
    emit(ToolCall + ToolResult в Thinking Block)
    history.add(toolResult)
    iterations++
}
```

## Инструменты агента

| Инструмент | Реализация | Описание |
|---|---|---|
| `git_log` | ProcessBuilder → git | История коммитов |
| `git_diff` | ProcessBuilder → git | Изменения в рабочей директории |
| `git_status` | ProcessBuilder → git | Статус репозитория |
| `git_branches` | ProcessBuilder → git | Список веток |
| `github_list_prs` | GitHub REST API | Список pull request |
| `github_list_issues` | GitHub REST API | Список issues |
| `telegram_send` | Telegram Bot API | Отправка сообщения |
| `file_read` | java.io.File | Чтение файла |
| `file_write` | java.io.File | Запись файла |
| `file_list` | java.io.File | Список файлов в директории |
| `get_time` | ZonedDateTime | Текущее время |
| `get_weather` | Open-Meteo (бесплатно) | Погода по городу |
| `get_currency` | Frankfurter (бесплатно) | Курс валют |
| `rag_search` | DocumentIndexer + VectorStore | Поиск по проиндексированным документам |

## RAG

1. Указываешь папку с документами (.md, .txt) в Settings
2. `DocumentIndexer` разбивает на чанки, получает эмбеддинги (GigaChat или Ollama `nomic-embed-text`)
3. Чанки и векторы хранятся в SQLite (`~/.god-agent/rag.db`)
4. LLM вызывает `rag_search(query)` как любой другой инструмент когда нужен контекст

## UI

```
+-----------------------------------------------+
| [Chat]                          [Settings]    |
+-----------------------------------------------+
|  User: Найди баг в последнем коммите           |
|        и напиши в TG                          |
|                                               |
|  > [▼ Thinking]  ← серый блок, expandable    |
|    🔧 git_log({"n": 5})                       |
|      ↳ a3f9c12 — fix NullPointerException...  |
|    🔧 git_diff({"sha": "a3f9c12"})            |
|      ↳ Main.kt:42 — null dereference          |
|    🔧 telegram_send({"text": "Баг: NPE..."})  |
|      ↳ OK                                     |
|                                               |
|  God Agent: Нашёл NPE в Main.kt:42.           |
|  Отправил в Telegram.                         |
|                                               |
+-----------------------------------------------+
|  [🎤] [         Введите сообщение...   ] [■]  |
+-----------------------------------------------+
```

- **Thinking Block** — раскрывающийся серый блок, каждый шаг добавляется в реальном времени
- **Stop** (красная кнопка) — отмена агента в любой момент через coroutine cancellation
- **Кнопка 🎤** — ручной старт/стоп записи голоса
- **Пассивное прослушивание** (wake word) — работает в фоне постоянно при включённом голосовом режиме

## Доступные LLM

### GigaChat (облако, требует ключ)
- `GigaChat` — базовая
- `GigaChat-Pro` — продвинутая
- `GigaChat-Max` — максимальная

### Ollama (локально, автообнаружение)
Приложение определяет запущенный Ollama и показывает список доступных моделей.

## Используемые технологии

| Технология | Версия | Роль |
|---|---|---|
| **Kotlin** | 2.1.0 | Язык разработки |
| **Compose Desktop** | 1.7.3 | UI-фреймворк |
| **Compose Material 3** | — | Дизайн-система |
| **Vosk** | 0.3.45 | Офлайн-распознавание речи (русская модель) |
| **JNA** | 5.8.0 | Нативный биндинг libvosk в обход сломанного `LibVosk` |
| **javax.sound.sampled** | JDK | Захват аудио с микрофона |
| **Ktor Client** | 3.0.2 | HTTP-клиент для GigaChat, Ollama, GitHub, Telegram API |
| **OkHttp engine** | — | Движок Ktor для JVM |
| **Kotlinx Coroutines** | 1.9.0 | Асинхронность, Flow для ReAct loop и аудиопотока |
| **Kotlinx Serialization** | 1.7.3 | JSON-сериализация, ReAct JSON-протокол |
| **Kotlinx DateTime** | 0.6.1 | Работа с датами |
| **Koin** | 4.0.0 | Dependency Injection |
| **sqlite-jdbc** | 3.45.1.0 | SQLite для RAG VectorStore |
| **JDK** | 17 | Минимальная версия Java |

## Архитектура

```
day-33-god-agent/
├── src/main/kotlin/ru/chtcholeg/godagent/
│   ├── Main.kt                              # Точка входа
│   ├── App.kt                               # Навигация Chat ↔ Settings
│   ├── di/
│   │   └── AppModule.kt                     # Koin DI — граф зависимостей
│   ├── data/
│   │   ├── api/
│   │   │   ├── GigaChatApi.kt               # GigaChat REST API (OAuth2 + chat)
│   │   │   ├── OllamaApi.kt                 # Ollama REST API
│   │   │   └── ApiModels.kt                 # DTO запросов/ответов
│   │   ├── audio/
│   │   │   ├── AudioRecorder.kt             # Захват микрофона → Flow<ByteArray>
│   │   │   └── VoskSpeechRecognitionService.kt  # JNA-биндинг Vosk, автозагрузка модели
│   │   ├── tools/
│   │   │   ├── AgentTool.kt                 # interface AgentTool + sealed class AgentStep
│   │   │   ├── ToolExecutor.kt              # Реестр инструментов, dispatch, статусы
│   │   │   ├── GitTool.kt                   # ProcessBuilder → git commands
│   │   │   ├── GitHubTool.kt                # GitHub REST API
│   │   │   ├── TelegramTool.kt              # Telegram Bot API
│   │   │   ├── FileOpsTool.kt               # java.io.File операции
│   │   │   └── UtilityTools.kt              # Weather (Open-Meteo), Currency (Frankfurter), Time
│   │   ├── rag/
│   │   │   ├── DocumentIndexer.kt           # Чанкинг + эмбеддинги
│   │   │   ├── VectorStore.kt               # SQLite + cosine similarity
│   │   │   └── RagTool.kt                   # AgentTool-обёртка над RAG
│   │   └── repository/
│   │       ├── ChatRepository.kt            # ReAct Loop + компрессия истории
│   │       ├── SettingsRepository.kt        # Настройки (JSON-файл на диске)
│   │       └── SessionRepository.kt         # История сессий (JSON-файл на диске)
│   ├── domain/model/
│   │   ├── AppSettings.kt                   # AppSettings, UserProfile, VoiceKeywords, ModelParameters
│   │   ├── AgentStep.kt                     # sealed: ToolCall, ToolResult, FinalAnswer, Error
│   │   ├── ChatMessage.kt                   # + роли THINKING, TOOL_CALL, TOOL_RESULT, SUMMARY
│   │   ├── ChatSession.kt
│   │   └── ModelInfo.kt                     # GigaChatModel, OllamaModel
│   └── presentation/
│       ├── chat/
│       │   ├── ChatStore.kt                 # MVI Store: ReAct, голос, StopAgent
│       │   ├── ChatIntent.kt                # Все интенты включая StopAgent
│       │   ├── ChatState.kt                 # + agentSteps, isAgentRunning
│       │   ├── ChatScreen.kt                # Экран чата с Thinking Block
│       │   └── ThinkingBlock.kt             # Раскрывающийся блок шагов агента
│       ├── settings/
│       │   └── SettingsScreen.kt            # LLM, RAG, Git, Telegram, GitHub, Voice, статусы
│       ├── components/
│       │   ├── MessageInput.kt              # Поле ввода с кнопкой Stop
│       │   ├── MessageList.kt
│       │   └── MessageItem.kt               # Поддержка tool_call/tool_result ролей
│       └── theme/
│           └── Theme.kt
└── build.gradle.kts
```

### Паттерн MVI + ReAct

`ChatStore` оркестрирует:
- `isAgentRunning` — агент выполняет ReAct loop
- `agentSteps` — список шагов для Thinking Block
- `isPassiveListening` / `isRecording` — голосовой пайплайн (из day-32)
- `cancelJob` — coroutine job для отмены через Stop

## Запуск

### Требования

- JDK 17+
- IntelliJ IDEA / Android Studio
- Микрофон (опционально, для голосового управления)

### Настройка

Создайте `local.properties` в корне проекта:

```properties
gigachat.clientId=ваш_client_id
gigachat.clientSecret=ваш_client_secret
```

> Для работы с Ollama ключи не нужны — достаточно запустить `ollama serve` локально.

Настройки сохраняются в `~/.god-agent/settings.json`.

### Первый запуск

При первом включении голосового управления модель Vosk (~50 МБ) скачивается автоматически в `~/.config/voice-agent/models/`.

### Сборка и запуск

```bash
# Запуск в режиме разработки
./gradlew run

# Сборка дистрибутива
./gradlew packageDmg      # macOS
./gradlew packageMsi      # Windows
./gradlew packageDeb      # Linux
```

## Конфигурация в Settings

| Секция | Параметры |
|---|---|
| **Профиль** | Имя агента, имя пользователя, язык, детализация, примеры |
| **LLM** | Провайдер (GigaChat / Ollama), credentials, модель, temperature, topP, maxTokens, max iterations |
| **RAG** | Папка с документами, кнопка Re-index, статус индексации |
| **Git** | Путь к репозиторию |
| **Telegram** | Bot Token, Chat ID |
| **GitHub** | Personal Access Token (опционально) |
| **Голос** | Вкл/выкл, wake word, стоп-слова |
| **Инструменты** | Статус каждого инструмента (доступен / недоступен) |

## Связь с предыдущими днями

- **Day 32** — голосовой агент (Vosk STT) — база проекта
- **Day 31** — персонализация (UserProfile, buildSystemPrompt) — переиспользована
- **Day 26–28** — интеграция Ollama — переиспользована `OllamaApi`
- **Day 20** — RAG (DocumentIndexer, VectorStore) — архитектура переиспользована
- **Day 21–24** — инструменты Git/GitHub/Telegram — логика переиспользована
- **Day 9** — компрессия истории диалога — паттерн переиспользован

## Видео

- https://disk.yandex.ru/i/AjJcI57eSVrP4A
