# AI Agent - ClaudeCode-like Interface

Кросс-платформенное Kotlin/Compose приложение с интерфейсом похожим на ClaudeCode.

## Статус проекта

⚠️ **В разработке** - Базовая структура создана, но требуются исправления перед сборкой.

## Структура проекта

```
ai-agent/
├── src/
│   ├── commonMain/kotlin/ru/chtcholeg/agent/
│   │   ├── domain/model/          # Доменные модели
│   │   │   ├── AgentMessage.kt    # Сообщения агента
│   │   │   ├── Model.kt           # AI модели (GigaChat, HuggingFace)
│   │   │   ├── AiSettings.kt      # Настройки AI
│   │   │   ├── McpServer.kt       # Конфигурация MCP серверов
│   │   │   └── McpTool.kt         # MCP инструменты
│   │   ├── data/
│   │   │   ├── api/               # API клиенты
│   │   │   ├── model/             # DTO модели
│   │   │   ├── mcp/               # MCP клиенты
│   │   │   └── repository/        # Репозитории
│   │   ├── presentation/
│   │   │   ├── agent/             # Главный экран агента
│   │   │   ├── settings/          # Экран настроек
│   │   │   ├── components/        # UI компоненты
│   │   │   └── theme/             # Material Design тема
│   │   ├── di/                    # Koin DI
│   │   └── App.kt                 # Главный Composable
│   ├── androidMain/              # Android-специфичный код
│   └── desktopMain/              # Desktop-специфичный код
└── build.gradle.kts
```

## Возможности

### Реализовано:
- ✅ MVI архитектура (Store/State/Intent pattern)
- ✅ Поддержка нескольких AI моделей (GigaChat, HuggingFace)
- ✅ MCP (Model Context Protocol) интеграция
- ✅ Настройка AI параметров (temperature, topP, maxTokens)
- ✅ Управление MCP серверами через UI
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
- `AI` - Ответ AI модели
- `TOOL_CALL` - Вызов MCP инструмента
- `TOOL_RESULT` - Результат выполнения инструмента
- `SYSTEM` - Системное сообщение
- `ERROR` - Ошибка

## Требования

- Kotlin 2.1.0+
- Gradle 8.10+
- JDK 17+
- Android SDK 36 (для Android)

## Зависимости

- **Compose Multiplatform** 1.7.3
- **Ktor** 3.0.2 - HTTP клиент
- **Kotlinx Serialization** - JSON сериализация
- **Koin** 4.0.0 - Dependency Injection
- **Kotlinx Coroutines** - Асинхронность
- **Kotlinx DateTime** - Работа с датами

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

## Известные проблемы

1. ⚠️ Требуется исправление сигнатур методов в AgentRepository
2. ⚠️ Отсутствует ic_launcher icon для Android
3. ⚠️ Ошибки в вызовах suspend функций в SettingsScreen
4. ⚠️ Необходима корректировка McpClientManager API

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
- **McpRepository**: Управление MCP серверами
- **SettingsRepository**: Хранение настроек AI

## Следующие шаги

1. Исправить ошибки компиляции
2. Добавить ic_launcher icon
3. Протестировать интеграцию с MCP серверами
4. Добавить персистентное хранение настроек
5. Улучшить обработку ошибок
6. Добавить unit тесты

## Лицензия

Проект создан в рамках AI Advent Challenge with Love.
