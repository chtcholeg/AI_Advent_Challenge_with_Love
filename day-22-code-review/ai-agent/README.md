# AI Agent - ClaudeCode-like Interface

Кросс-платформенное Kotlin/Compose приложение с интерфейсом похожим на ClaudeCode.

## Статус проекта

**Готов к использованию** - Полнофункциональное приложение с MVI архитектурой, MCP интеграцией, RAG поддержкой и системой команд.

## Структура проекта

```
ai-agent/
├── src/
│   ├── commonMain/kotlin/ru/chtcholeg/agent/
│   │   ├── domain/
│   │   │   ├── model/             # Доменные модели
│   │   │   │   ├── AgentMessage.kt    # Сообщения агента (USER, AI, COMMAND, etc.)
│   │   │   │   ├── CommandResult.kt   # Результаты команд (Day 21)
│   │   │   │   ├── Model.kt          # AI модели (GigaChat, HuggingFace)
│   │   │   │   ├── AiSettings.kt     # Настройки AI
│   │   │   │   ├── RagMode.kt        # Режимы RAG
│   │   │   │   └── McpServer.kt      # Конфигурация MCP серверов
│   │   │   └── service/           # Доменные сервисы
│   │   │       ├── CommandHandler.kt       # Обработчик команд (Day 21)
│   │   │       └── ProjectRootProvider.kt  # Поиск README.md (Day 21)
│   │   ├── data/
│   │   │   ├── api/               # API клиенты
│   │   │   ├── model/             # DTO модели
│   │   │   ├── local/             # SQLDelight хранилище
│   │   │   ├── mcp/               # MCP клиенты
│   │   │   └── repository/        # Репозитории (Agent, MCP, RAG, Settings)
│   │   ├── presentation/
│   │   │   ├── agent/             # Главный экран агента
│   │   │   ├── settings/          # Экран настроек
│   │   │   ├── components/        # UI компоненты
│   │   │   └── theme/             # Material Design тема
│   │   ├── di/                    # Koin DI
│   │   └── App.kt                 # Главный Composable
│   ├── androidMain/              # Android-специфичный код
│   └── desktopMain/              # Desktop-специфичный код
│       └── domain/service/
│           └── ProjectRootProvider.desktop.kt  # Git MCP Detection (Day 21)
└── build.gradle.kts
```

## Возможности

### Реализовано:
- ✅ MVI архитектура (Store/State/Intent pattern)
- ✅ Поддержка нескольких AI моделей (GigaChat, HuggingFace)
- ✅ MCP (Model Context Protocol) интеграция
- ✅ Git MCP Server для работы с репозиториями (Day 21)
- ✅ Система команд (`/help`) с умным поиском README.md (Day 21)
- ✅ RAG (Retrieval-Augmented Generation) с семантическим поиском
- ✅ Отображение цитат и источников из документов
- ✅ Reranking для улучшения качества поиска
- ✅ Настройка AI параметров (temperature, topP, maxTokens)
- ✅ Управление MCP серверами через UI
- ✅ Поддержка скриншотов в контексте
- ✅ Material Design 3 UI
- ✅ Koin dependency injection

### UI компоненты:
- **AgentScreen**: Главный экран с историей сообщений и input полем
- **SettingsScreen**: Настройки AI параметров и MCP серверов
- **MessageList**: Прокручиваемый список сообщений
- **MessageInput**: Поле ввода с кнопкой отправки
- **MessageItem**: Отображение отдельного сообщения с метаданными

### Типы сообщений:
- `USER` - Сообщение пользователя
- `AI` - Ответ AI модели (с поддержкой цитат из источников)
- `TOOL_CALL` - Вызов MCP инструмента
- `TOOL_RESULT` - Результат выполнения инструмента
- `COMMAND` - Результат локальной команды (`/help`)
- `SCREENSHOT` - Изображение скриншота
- `RAG_CONTEXT` - Информация о найденных документах
- `SYSTEM` - Системное сообщение
- `ERROR` - Ошибка

### RAG (Retrieval-Augmented Generation):
- **Векторный поиск**: Семантический поиск по индексированным документам
- **Reranking**: Двухэтапная фильтрация результатов (порог + gap detection)
- **Цитаты**: Отображение фрагментов текста из источников
- **Источники**: Кликабельные ссылки на документы с метаданными
- **Релевантность**: Процент соответствия каждого источника запросу

### Git-based README Detection (Desktop):
Система поиска README.md использует три стратегии в приоритетном порядке:

**Strategy 1: Git MCP Detection** (новая!)
- Использует Git MCP Server для определения активной рабочей директории
- **Комбинированный подход**:
  1. Вызывает `git_status` для получения текущих изменённых файлов (staged/unstaged)
  2. Если изменений нет, вызывает `git_log` для анализа файлов последнего коммита
  3. Находит общую родительскую директорию всех файлов
  4. Ищет README.md в этой директории
- **Преимущества**: Автоматически определяет контекст работы разработчика
- **Файл**: `ProjectRootProvider.desktop.kt:88-173`

**Strategy 2: Git Root Search**
- Ищет `.git` папку вверх по иерархии директорий
- Ищет README.md в корне Git репозитория
- **Файл**: `ProjectRootProvider.desktop.kt:239-249`

**Strategy 3: Hierarchy Search**
- Поиск вверх по иерархии директорий (до 5 уровней)
- Фоллбэк стратегия, если Git недоступен
- **Файл**: `ProjectRootProvider.desktop.kt:34-50`

**Интеграция с MCP**:
- MCP репозиторий передаётся через Koin DI (`Koin.kt:72`)
- На Android используется статический README (Git MCP не поддерживается)
- Логирование в консоль для отладки (`[ProjectRootProvider]` префикс)

## Требования

- Kotlin 2.1.0+
- Gradle 8.10+
- JDK 17+
- Android SDK 36 (для Android)

## Зависимости

- **Compose Multiplatform** 1.7.3 - UI framework
- **Ktor** 3.0.2 - HTTP клиент
- **Kotlinx Serialization** 1.7.3 - JSON сериализация
- **Koin** 4.0.0 - Dependency Injection
- **Kotlinx Coroutines** 1.9.0 - Асинхронность
- **Kotlinx DateTime** 0.6.1 - Работа с датами
- **SQLDelight** 2.0.2 - Кроссплатформенная база данных

## Конфигурация

Создайте файл `local.properties` в корне проекта с credentials:

```properties
gigachat.clientId=your_client_id
gigachat.clientSecret=your_client_secret
huggingface.apiToken=your_token
```

## Сборка

```bash
# Сборка всего проекта
./gradlew :ai-agent:build

# Запуск Desktop версии
./gradlew :ai-agent:run

# Сборка Android APK
./gradlew :ai-agent:assembleDebug
```

## Известные ограничения

1. Git MCP Detection доступен только на Desktop (на Android используется статический контент)
2. Функция вызова инструментов (function calling) поддерживается только моделями GigaChat

## Архитектура

### MVI Pattern

```
User Action → Intent → Store → Repository → API/MCP
                ↓
            State Update
                ↓
            UI Recompose
```

- **AgentStore**: Управляет состоянием главного экрана
- **AgentState**: Immutable UI state (messages, loading, error)
- **AgentIntent**: User actions (SendMessage, ClearChat, ReloadTools)

### Repositories

- **AgentRepository**: Оркестрация запросов к AI и MCP
- **McpRepository**: Управление MCP серверами и инструментами
- **RagRepository**: RAG с семантическим поиском по документам
- **SettingsRepository**: Хранение настроек AI

### Services

- **CommandHandler**: Обработка команд (`/help`), парсинг и маршрутизация
- **ProjectRootProvider**: Поиск README.md (Git MCP + Git Root + Hierarchy)

## Возможные улучшения

1. Дополнительные команды (`/status`, `/clear`, `/model`, `/export`)
2. Автодополнение команд в UI
3. Дополнительные Git MCP tools (`git_stash`, `git_merge`, `git_rebase`)
4. Unit тесты для CommandHandler и ProjectRootProvider
5. История команд с навигацией стрелками

## Лицензия

Проект создан в рамках AI Advent Challenge with Love.
