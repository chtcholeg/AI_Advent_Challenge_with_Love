# God Agent — Спецификация (Day 33)

## Цель

Kotlin Desktop-приложение «God Agent» — личный AI-сотрудник, объединяющий голосовой ввод, RAG, MCP-инструменты (Git, GitHub, Telegram, FileOps, Weather, Currency, Time) и персонализацию в единый ReAct-агент с гибридной маршрутизацией.

---

## Стек

| Компонент | Решение |
|---|---|
| База проекта | day-32 (voice agent) |
| Модульность | Один модуль, Desktop only |
| UI | Compose Desktop 1.7.3, Material3 |
| HTTP | Ktor Client 3.0.2 + OkHttp |
| Async | Kotlinx Coroutines 1.9.0 |
| DI | Koin 4.0.0 |
| Persistence | SQLDelight 2.0.2 |
| STT | Vosk (offline, из day-32) |
| LLM | GigaChat или Ollama (выбор в Settings) |
| Embeddings | Автоматически: GigaChat → GigaChat API; Ollama → Ollama embeddings |

---

## Архитектура

### Поток обработки запроса

```
[Голос (Vosk) / Текст]
         ↓
[Слой персонализации] — только стиль ответа (тон, язык, детализация)
         ↓
[LLM Call #1: Classifier]
   "Запрос simple или complex? Какие инструменты нужны?"
         ↓
   ┌─────────────────────────┐
   │ Simple                  │ Complex
   │ → прямой ответ          │ → ReAct Loop
   │   (0–1 tool call)       │   (N итераций, макс. — настраивается)
   └─────────────────────────┘
         ↓                         ↓
   [Ответ]            [Thinking Block обновляется в реальном времени]
                      [→ финальный ответ]
```

### ReAct Loop

- Один большой системный промпт со всеми MCP-инструментами + rag_search
- LLM сам решает, что вызывать и в каком порядке
- Каждый tool call немедленно отображается в Thinking Block
- При ошибке MCP: tool возвращает `tool_error`, LLM видит ошибку и сам решает — попробовать альтернативный инструмент или сообщить пользователю
- Пользователь может нажать Stop → coroutine cancellation → агент останавливается

### История

- Единая история на весь чат (все MCP-вызовы, RAG-ответы, сообщения — в одной цепочке)
- Компрессия: когда суммарное количество токенов в истории > 4000 — старые сообщения сжимаются в summary (как в day-9)
- Persistence: SQLDelight, история сохраняется между запусками

---

## MCP-инструменты

### Список серверов

| Сервер | Источник | Инструменты |
|---|---|---|
| Git | day-21 | git_log, git_diff, git_status, git_commit, git_push, git_pull, git_branches, git_blame |
| GitHub | day-14 | gh_list_prs, gh_get_pr, gh_list_issues, gh_get_issue |
| Telegram | day-13/14 | tg_send_message, tg_read_channel |
| FileOps | day-14 | file_read, file_write, file_list |
| Weather | day-11/14 | get_weather |
| Currency | day-14 | get_exchange_rate |
| Time | day-14 | get_time |

### Lifecycle

- Все серверы запускаются автоматически при старте приложения
- Путь к Git-репозиторию: текущая директория приложения по умолчанию, настраивается в Settings
- При падении сервера: `tool_error` в ответе LLM, индикатор статуса в Settings меняется на «недоступен»
- В Settings: кнопка restart для каждого сервера

---

## RAG

- `rag_search(query: String)` — один из MCP-инструментов (встроенный, не внешний процесс)
- LLM вызывает его самостоятельно когда нужно — как любой другой tool
- Папка с документами: настраивается в Settings, кнопка «Re-index»
- Индексация при старте если папка задана
- Embeddings: автоматически по провайдеру LLM (GigaChat → GigaChat API, Ollama → Ollama)
- Хранилище векторов: SQLDelight

---

## Голос

- Vosk offline STT (модель `vosk-model-small-ru-0.22`)
- Пассивное слушание с wake word (настраивается в Settings)
- Активная запись после wake word → стоп-слово «отправить» → отправка
- Стоп-слово «отмена» → сброс записи
- Все слова настраиваются в Settings

---

## Персонализация

Влияет **только на стиль ответа** (тон, язык, уровень детализации, примеры).
На маршрутизацию и выбор инструментов **не влияет**.

Профиль:
- Имя пользователя
- Язык ответа (русский / английский)
- Уровень детализации: BRIEF / MEDIUM / DETAILED
- Использовать примеры: да / нет
- Дополнительное описание (free text)

Промпт перестраивается при каждом обращении на основе профиля.

---

## UI

### Навигация

```
[Chat]                    [Settings]
```
Два экрана, nav bar сверху (как в day-31/32).

### Chat Screen

```
+-----------------------------------------------+
| [Chat]                          [Settings]    |
+-----------------------------------------------+
|  User: Найди баг в последнем коммите           |
|        и напиши в TG                          |
|                                               |
|  > [▼ Thinking]  ← серый блок, expandable    |
|    • Routing → complex                        |
|    • git_log → SHA: a3f9c12                   |
|    • git_diff → 47 lines                      |
|    • Анализ: NullPointerException в Main.kt   |
|    • telegram_send → ✓ OK                     |
|                                               |
|  God Agent: Нашёл NPE в Main.kt:42.           |
|  Отправил в Telegram.                         |
|                                               |
+-----------------------------------------------+
|  [🎤] [              Введите сообщение...  ] [■ Stop]  |
+-----------------------------------------------+
```

- Thinking Block: раскрывающийся серый блок перед финальным ответом
- Каждый шаг добавляется в реальном времени по мере выполнения tool call
- Кнопка Stop видна только пока агент работает
- Кнопка 🎤 (микрофон): старт/стоп ручной записи
- Пассивное слушание (wake word) работает в фоне всегда

### Settings Screen

Секции:

**Профиль**
- Имя агента (используется как заголовок приложения)
- Имя пользователя, язык, детализация, примеры

**LLM**
- Provider: GigaChat / Ollama
- Credentials (client_id, client_secret для GigaChat; URL для Ollama)
- Модель, temperature, topP, maxTokens
- Max ReAct iterations (slider 1–20)

**RAG**
- Папка документов (FileChooser)
- Кнопка Re-index
- Статус: N документов проиндексировано

**Голос**
- Вкл/выкл
- Wake word (text field)
- Stop-send word, Stop-cancel word

**Git / MCP**
- Путь к Git-репозиторию (FileChooser)
- Список MCP-серверов с индикаторами ● (зелёный/красный) и кнопкой Restart

---

## Модели данных

```kotlin
data class AppSettings(
    val agentName: String,              // "God Agent"
    val userProfile: UserProfile,
    val llmProvider: LlmProvider,       // GIGACHAT / OLLAMA
    val gigaChatCredentials: GigaChatCredentials?,
    val ollamaConfig: OllamaConfig?,
    val modelParameters: ModelParameters,
    val maxReactIterations: Int,        // default: 10
    val ragFolderPath: String?,
    val gitRepoPath: String?,
    val voiceKeywords: VoiceKeywords,
    val mcpServersEnabled: Map<McpServer, Boolean>
)

data class UserProfile(
    val userName: String,
    val language: String,               // "ru" / "en"
    val detailLevel: DetailLevel,       // BRIEF / MEDIUM / DETAILED
    val useExamples: Boolean,
    val additionalDescription: String
)

data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,              // USER / ASSISTANT / TOOL_CALL / TOOL_RESULT / THINKING / SUMMARY
    val timestamp: Long,
    val toolName: String?,
    val executionTimeMs: Long?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val isSummary: Boolean = false
)

enum class McpServer { GIT, GITHUB, TELEGRAM, FILE_OPS, WEATHER, CURRENCY, TIME }
```

---

## Системный промпт (структура)

```
Ты — персональный AI-ассистент {agentName}.
Пользователь: {userName}. Язык ответов: {language}.
Стиль: {detailLevel}. Примеры: {useExamples}.
{additionalDescription}

У тебя есть следующие инструменты:
<tools>
  git_log, git_diff, git_status, git_commit, git_push,
  git_pull, git_branches, git_blame,
  gh_list_prs, gh_get_pr, gh_list_issues,
  tg_send_message, tg_read_channel,
  file_read, file_write, file_list,
  get_weather, get_exchange_rate, get_time,
  rag_search
</tools>

Сначала определи: запрос простой (ответить без инструментов или с одним вызовом)
или сложный (нужен пошаговый план и несколько вызовов)?
Для сложных — работай итерационно: думай → действуй → смотри результат → думай снова.
Максимум {maxReactIterations} итераций.
```

---

## Модульная структура (один модуль)

```
src/main/kotlin/ru/chtcholeg/godagent/
├── Main.kt                          # Entry point
├── App.kt                           # Navigation (Chat / Settings)
├── di/
│   └── AppModule.kt                 # Koin DI
├── data/
│   ├── api/
│   │   ├── GigaChatApi.kt
│   │   └── OllamaApi.kt
│   ├── mcp/
│   │   ├── McpClientManager.kt      # Lifecycle MCP серверов
│   │   ├── GitMcpServer.kt
│   │   ├── GitHubMcpServer.kt
│   │   ├── TelegramMcpServer.kt
│   │   ├── FileOpsMcpServer.kt
│   │   └── UtilMcpServer.kt         # Weather + Currency + Time
│   ├── rag/
│   │   ├── DocumentIndexer.kt       # Chunking + embeddings
│   │   ├── VectorStore.kt           # SQLDelight
│   │   └── RagSearchTool.kt         # Инструмент rag_search
│   ├── audio/
│   │   ├── AudioRecorder.kt
│   │   └── VoskSpeechRecognitionService.kt
│   └── repository/
│       ├── ChatRepository.kt        # LLM вызовы + ReAct loop
│       ├── SettingsRepository.kt
│       └── SessionRepository.kt    # SQLDelight история
├── domain/
│   └── model/                       # AppSettings, ChatMessage, ...
└── presentation/
    ├── chat/
    │   ├── ChatScreen.kt
    │   ├── ChatStore.kt             # Orchestrator (StateFlow)
    │   └── ThinkingBlock.kt         # Expandable thinking UI
    └── settings/
        ├── SettingsScreen.kt
        └── McpStatusPanel.kt
```

---

## Ключевые алгоритмы

### ReAct Loop (в ChatRepository)

```kotlin
suspend fun sendMessage(userMessage: String): Flow<AgentStep> = flow {
    // 1. Добавить в историю
    history.add(Message(role=USER, content=userMessage))

    var iterations = 0
    val maxIterations = settings.maxReactIterations

    while (iterations < maxIterations) {
        val response = llmApi.chat(
            messages = buildHistory(),
            tools = allTools,
            systemPrompt = buildSystemPrompt()
        )

        if (response.toolCalls.isEmpty()) {
            // Финальный ответ
            emit(AgentStep.FinalAnswer(response.content))
            history.add(Message(role=ASSISTANT, content=response.content))
            break
        }

        // Есть tool calls
        for (toolCall in response.toolCalls) {
            emit(AgentStep.ToolCall(toolCall.name, toolCall.arguments))
            val result = executeTool(toolCall)
            emit(AgentStep.ToolResult(toolCall.name, result))
            history.add(Message(role=TOOL_RESULT, toolName=toolCall.name, content=result))
        }

        iterations++
    }

    if (iterations >= maxIterations) {
        emit(AgentStep.Error("Превышен лимит итераций ($maxIterations)"))
    }

    // Компрессия если нужна
    if (countTokens(history) > 4000) compressHistory()
}
```

### Thinking Block

ChatStore собирает `AgentStep` из flow и формирует список строк для ThinkingBlock:
- `ToolCall(name, args)` → `"🔧 $name($args)"`
- `ToolResult(name, result)` → `"  ↳ ${result.take(100)}"`
- `FinalAnswer` → закрывает Thinking Block, показывает финальный ответ

### История + Компрессия

- При старте загружается последние N сообщений из SQLDelight
- При > 4000 токенов: первые 60% сообщений суммаризируются одним LLM-вызовом → один `SUMMARY` message
- SQLDelight таблица: `messages(id, session_id, role, content, tool_name, timestamp, is_summary)`

---

## MCP Server Lifecycle

```kotlin
class McpClientManager {
    private val clients = mutableMapOf<McpServer, McpClient>()

    suspend fun startAll(settings: AppSettings) {
        McpServer.values().forEach { server ->
            if (settings.mcpServersEnabled[server] != false) {
                try {
                    clients[server] = createClient(server, settings)
                    updateStatus(server, McpStatus.RUNNING)
                } catch (e: Exception) {
                    updateStatus(server, McpStatus.FAILED)
                }
            }
        }
    }

    suspend fun callTool(toolName: String, args: JsonObject): String {
        val server = resolveServer(toolName)
        return try {
            clients[server]?.callTool(toolName, args) ?: "Сервер $server недоступен"
        } catch (e: Exception) {
            "tool_error: ${e.message}"
        }
    }
}
```

---

## Не входит в скоуп Day 33

- Android-таргет
- Мультисессионность (несколько чатов)
- Авто-уведомления/проактивность агента
- PM/CRM MCP серверы (day-24)
- Streaming токенов от LLM (ответ показывается целиком после генерации)

---

## Порядок реализации

1. Скопировать day-32, переименовать пакет → `ru.chtcholeg.godagent`
2. Добавить SQLDelight (зависимости + схема)
3. Реализовать `McpClientManager` + подключить MCP серверы
4. Реализовать `RagSearchTool` (DocumentIndexer + VectorStore)
5. Реализовать ReAct Loop в `ChatRepository`
6. Подключить Thinking Block в UI
7. Расширить Settings Screen (RAG папка, Git путь, MCP статусы, max iterations)
8. Интеграционное тестирование: голос → ReAct → Telegram/Git
