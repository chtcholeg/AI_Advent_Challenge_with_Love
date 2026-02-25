# AI Advent Challenge with Love

Образовательный проект, демонстрирующий поэтапное создание AI-чат приложения на **Kotlin Compose Multiplatform** с интеграцией GigaChat API. Каждый день добавляет новую концепцию из мира LLM и AI-инструментов — от простого чата до полноценных продуктовых приложений с RAG, MCP-серверами, мульти-агентными пайплайнами и локальными LLM.

## Технологии

| Категория | Стек |
|-----------|------|
| Язык | Kotlin Multiplatform |
| UI | Compose Multiplatform (Material Design 3) |
| Архитектура | MVI (Model-View-Intent) |
| HTTP | Ktor |
| DI | Koin |
| Сериализация | Kotlinx Serialization |
| База данных | SQLDelight (с Day 10) |
| AI-бэкенд | GigaChat API, Hugging Face API |
| Платформы | Android, Desktop (JVM), Web (WasmJs — Days 1-6) |

## Структура проекта

```
AI_Advent_Challenge_with_Love/
├── day-01-simple-chat/                 # Базовый чат
├── day-02-structured-response/         # Структурированные JSON-ответы
├── day-03-dialog/                      # Диалоговый режим
├── day-04-different-reasoning/         # Режимы рассуждений
├── day-05-change-system-prompt-during-dialog/  # Смена системного промпта
├── day-06-temperature/                 # Температура и копирование сообщений
├── day-07-different-models/            # Мультимодельность
├── day-08-counting-tokens/             # Подсчёт токенов и метаданные
├── day-09-dialog-compression/          # Сжатие диалогов
├── day-10-saving-history/              # Сохранение истории в SQLite
├── day-11-simple-mcp-server/           # Первый MCP-сервер
├── day-12-using-mcp-server-tool/       # Использование MCP-инструментов
├── day-13-reminder-mcp/                # Telegram-напоминания через MCP
├── day-14-mcp-composition/             # Композиция MCP-серверов
├── day-15-environment/                 # Управление окружением
├── day-16-document-indexing/           # Индексация документов
├── day-17-rag-query/                   # RAG-запросы
├── day-18-rag-reranking/               # Ре-ранкинг результатов
├── day-19-citation-and-sources/        # Цитирование и источники
├── day-20-rag-chat/                    # Полноценный RAG-чат
├── day-21-developer-assistant/         # Developer Assistant с Git MCP
├── day-22-code-review/                 # Code Review Assistant
├── day-23-user-support-assistant/      # User Support Assistant
├── day-24-team-assistant/              # Team Assistant с PM MCP
├── day-25-real-task/                   # Реальные продукты: JuriLytics + VPS Manager
├── day-26-local-llm/                   # Локальный LLM через Ollama (Kotlin Multiplatform)
├── day-27-embedded-local-llm/          # Встроенный LLM на Android (Gemma 2 2B)
├── day-28-local-llm-on-vps/           # JuriLytics + VPS Manager с поддержкой Ollama на VPS
├── day-29-local-llm-optimization-and-adaptation/  # LLM Parameter Lab: JSON Extractor (Ollama + GigaChat)
├── day-30-local-analyst/              # Local Log Analyst: анализ логов и данных через Ollama
├── day-31-personalization/            # Персонализация: профиль пользователя → системный промпт
├── day-32-voice-agent/                # Голосовой агент: офлайн-STT Vosk, кодовые слова, пассивное прослушивание
└── day-33-god-agent/                  # God Agent: ReAct-агент с 13 инструментами, RAG, голосом и персонализацией
```

Каждая директория — самостоятельный проект, который можно собрать и запустить независимо.

## Вехи

### Phase 1: Основы чата (Days 1-3)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 1** | Simple Chat | Интеграция с GigaChat API, MVI-архитектура, Ktor HTTP-клиент, Koin DI, кроссплатформенность (Android/Desktop/Web) |
| **Day 2** | Structured Response | AI отвечает в строгом JSON-формате (summary, response, expert role, unicode symbols), переключение JSON/форматированный вид |
| **Day 3** | Dialog Mode | AI задаёт уточняющие вопросы по одному, последовательно собирает контекст, идеален для сбора требований |

### Phase 2: Продвинутые режимы и метаданные (Days 4-8)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 4** | Different Reasoning | Step-by-Step Reasoning (пошаговый разбор), Expert Panel Discussion (дискуссия экспертов) |
| **Day 5** | System Prompt Flexibility | Переключение режимов с сохранением/очисткой истории, XML-структурированные ответы |
| **Day 6** | Temperature | Копирование сообщений и всей переписки, кроссплатформенный буфер обмена, гайд по температуре |
| **Day 7** | Different Models | Поддержка нескольких AI-провайдеров (GigaChat, Llama, DeepSeek через Hugging Face) |
| **Day 8** | Counting Tokens | Отображение использованных токенов (input/output), время выполнения, название модели |

### Phase 3: Персистентность (Days 9-10)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 9** | Dialog Compression | Интеллектуальное сжатие истории диалога для экономии токенов с сохранением контекста |
| **Day 10** | Saving History | SQLDelight, локальная база данных, управление сессиями (создание/загрузка/архивация/удаление) |

### Phase 4: MCP-серверы и инструменты (Days 11-15)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 11** | Simple MCP Server | Kotlin/Ktor MCP-сервер с SSE-транспортом, API-ключ аутентификация, Weather Tool |
| **Day 12** | Using MCP Server Tool | Вызов MCP-инструментов из чата, маршрутизация запросов к инструментам |
| **Day 13** | Reminder MCP | Telegram-бот для напоминаний, локальный обработчик инструментов |
| **Day 14** | MCP Composition | Оркестрация 7 MCP-серверов, Docker-контейнеры для сервисов |
| **Day 15** | Environment | Динамическое управление MCP-серверами, конфигурация окружения |

### Phase 5: RAG и Developer Assistant (Days 16-21)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 16** | Document Indexing | DocumentLoader (MD/TXT), TextChunker (3 стратегии), EmbeddingService (GigaChat Embeddings), VectorStore (cosine similarity) |
| **Day 17** | RAG Query | Retrieve-Augment-Generate pipeline, векторный поиск в агенте, контекст из найденных документов |
| **Day 18** | RAG Reranking | Двухэтапный поиск: широкий поиск + фильтрация по релевантности, обнаружение разрывов в скорах |
| **Day 19** | Citation & Sources | Кликабельные ссылки на источники, FileOpener, индексация веб-страниц |
| **Day 20** | RAG Chat | Полноценный RAG-чат, объединяющий компоненты Days 16-19 |
| **Day 21** | Developer Assistant | Python Git MCP-сервер (11 git-операций), AI как ассистент разработчика |

### Phase 6: AI-агенты для реальных задач (Days 22-25)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 22** | Code Review Assistant | Команда `/review-pr`, специализированные чеклисты по технологиям (Kotlin Coroutines, MVI, SQL, Python Async и др.), детекция критических багов (race conditions, memory leaks, SQL injection), поддержка больших PR (>20 000 строк) |
| **Day 23** | User Support Assistant | Команда `/support`, CRM MCP-сервер (порт 8011) с управлением тикетами, умный поиск с LLM-расширением запросов и морфологическим анализом, двойные источники (RAG + CRM) |
| **Day 24** | Team Assistant | Команда `/task`, PM MCP-сервер (порт 8012) с полным CRUD задач, AI-анализ приоритетов, метрики проекта (velocity, workload, completion), интеграция Git + CRM + PM |
| **Day 25** | Real Task | **JuriLytics**: мульти-агентный анализ юридических документов (параллельные специализированные агенты, верификация, gap-checker, Q&A-чат); **VPS Manager**: веб-мастер настройки VPS с WebSocket, поддержкой OpenVPN и Python-деплоя, SFTP-загрузкой файлов |

### Phase 7: Локальные LLM (Days 26-30)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 26** | Local LLM (Kotlin) | Интеграция Ollama в Kotlin Multiplatform-приложение: `OllamaApi` с OpenAI-совместимым эндпоинтом, модели Qwen2.5 (0.5B и 32B), индикатор статуса сервера, поддержка tool calling и RAG-эмбеддингов (`nomic-embed-text`) |
| **Day 27** | Embedded Local LLM (Android) | Нативный Android-чат с Gemma 2 2B (INT4, CPU) через MediaPipe Tasks GenAI: полностью офлайн, потоковый вывод токен-за-токеном, MVVM + StateFlow |
| **Day 28** | Local LLM on VPS | JuriLytics с поддержкой Ollama на VPS: слой абстракции `llm.py` (GigaChat + Ollama), выбор модели per-user в SQLite, admin-панель настройки Ollama, проверка подключения к VPS-серверу |
| **Day 29** | LLM Parameter Lab | Интерактивный side-by-side стенд для сравнения параметров LLM (модель, квантование, temperature, num_ctx, max_tokens, system prompt) на задаче JSON-экстракции; поддержка Ollama и GigaChat; параллельный batch-режим |
| **Day 30** | Local Log Analyst | Веб-приложение для анализа логов через локальную LLM: загрузка CSV/JSON/SQLite/TXT, автодетект схемы, чат на естественном языке, генерация SQL + визуализация, streaming ответов, экспорт; утилита сбора отзывов из Google Play и RuStore |
| **Day 31** | Personalization | Персонализация AI: профиль пользователя (имя, язык, интересы, уровень детализации, стиль примеров) автоматически преобразуется в системный промпт; настройка параметров модели (temperature, Top P, Top K, Max Tokens, Repetition Penalty) через слайдеры; предпросмотр итогового промпта в реальном времени |

### Phase 8: Голосовое управление и агенты (Days 32-33)

| День | Тема | Что добавлено |
|------|------|---------------|
| **Day 32** | Voice Agent | Офлайн-распознавание речи через Vosk (`vosk-model-small-ru-0.22`), JNA-биндинг в обход сломанного `LibVosk` на macOS, пассивное прослушивание кодового слова (`компьютер`), активная запись с отображением текста в реальном времени, стоп-слова (`отправить` / `отмена`), автозагрузка модели (~45 МБ) при первом запуске, автоперезапуск прослушивания после каждого цикла |
| **Day 33** | God Agent | ReAct-агент с 13 встроенными инструментами (git, GitHub, Telegram, FileOps, Weather, Currency, Time, rag_search), prompt-engineering JSON протокол, Thinking Block с шагами в реальном времени, RAG на SQLite, компрессия истории, кнопка Stop, голосовой ввод через Vosk |

## Архитектура

```
Presentation Layer
├── chat/          MVI: ChatStore, ChatIntent, ChatState, ChatScreen
├── session/       Управление сессиями
├── settings/      Настройки AI-параметров
├── components/    MessageList, MessageItem, MessageInput
└── theme/         Material Design 3, тёмная тема

Domain Layer
├── model/         ChatMessage, ChatSession, AiResponse, AiSettings, ResponseMode, Model
└── usecase/       SendMessageUseCase

Data Layer
├── api/           GigaChatApiImpl, HuggingFaceApiImpl
├── repository/    ChatRepository, SettingsRepository, RagRepository
├── local/         SQLDelight (сессии, сообщения)
├── mcp/           McpRepository, интеграция с MCP-серверами
└── tool/          LocalToolHandler
```

## Быстрый старт

### Требования

- JDK 17+
- Android Studio / IntelliJ IDEA
- Android SDK (для Android-сборки)

### Настройка

1. Перейдите в директорию нужного дня:
   ```bash
   cd day-28-local-llm-on-vps
   ```

2. Создайте `local.properties` с API-ключами:
   ```properties
   gigachat.clientId=your_client_id
   gigachat.clientSecret=your_client_secret
   huggingface.apiToken=your_token
   ```

3. Получить ключи GigaChat: [GigaChat Developer Portal](https://developers.sber.ru/portal/products/gigachat)

### Запуск

```bash
# Desktop
./gradlew :composeApp:runDesktop

# Android
./gradlew :composeApp:installDebug

# Web (Days 1-6)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

## Режимы ответов AI

Приложение поддерживает 6 взаимоисключающих режимов:

1. **Normal** — стандартный диалог
2. **Structured JSON** — ответ в строгом JSON с метаданными
3. **Structured XML** — XML-альтернатива
4. **Dialog** — пошаговый сбор требований (по одному вопросу)
5. **Step-by-Step** — разбор задачи по шагам
6. **Expert Panel** — обсуждение несколькими экспертами с дебатами

## Лицензия

Проект создан в образовательных целях.
