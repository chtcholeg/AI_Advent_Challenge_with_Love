# Система команд AI Agent

## Описание

В AI Agent добавлена система команд, которые начинаются с косой черты (`/`). Команды обрабатываются локально и не отправляются в AI модель.

## Доступные команды

### `/help`

Отображает информацию о текущем проекте из README.md.

**Использование:**
```
/help
```

**Что показывает:**
- 📖 Project Overview - краткое описание проекта
- ✨ Current Features - основные возможности
- 🆕 Day 21 Updates - последние обновления
- 🚀 Quick Start - команды для запуска
- 💡 Available Commands - список доступных команд

**Платформы:**
- **Desktop**: Читает полное содержимое README.md из иерархии директорий проекта
- **Android**: Показывает статическое сообщение с основной информацией

## Архитектура

### Компоненты

1. **CommandHandler** (`domain/service/CommandHandler.kt`)
   - Обработчик команд
   - Парсинг команд и аргументов
   - Маршрутизация к соответствующим обработчикам

2. **ProjectRootProvider** (`domain/service/ProjectRootProvider.kt`)
   - Интерфейс для доступа к файлам проекта
   - Платформо-специфичная реализация (expect/actual)
   - Desktop: 3 стратегии поиска README.md (Git MCP + Git Root + Hierarchy)
   - Android: статический контент

3. **CommandResult** (`domain/model/CommandResult.kt`)
   - Sealed class для результата выполнения команды
   - Success: успешное выполнение
   - Error: ошибка выполнения

4. **MessageType.COMMAND** (`domain/model/AgentMessage.kt`)
   - Новый тип сообщения для результатов команд
   - Отображается зеленым цветом в UI

### Поток выполнения

```
User Input "/help"
    ↓
AgentStore.sendMessage()
    ↓
CommandHandler.isCommand() → true
    ↓
CommandHandler.handleCommand()
    ↓
ProjectRootProvider.readReadmeFile()
    ↓
Parse & Format Response
    ↓
CommandResult.Success
    ↓
Display as MessageType.COMMAND
```

## Добавление новых команд

### Шаг 1: Добавить команду в CommandHandler

```kotlin
return when (command) {
    "help" -> handleHelpCommand()
    "newcmd" -> handleNewCommand(args) // Новая команда
    else -> CommandResult.Error("Unknown command: /$command")
}
```

### Шаг 2: Реализовать обработчик

```kotlin
private suspend fun handleNewCommand(args: String?): CommandResult {
    return try {
        // Логика команды
        val result = performAction(args)
        CommandResult.Success(result)
    } catch (e: Exception) {
        CommandResult.Error("Failed: ${e.message}")
    }
}
```

### Шаг 3: Обновить /help

Добавить описание новой команды в метод `handleHelpCommand()`.

## Примеры использования

### Текущие команды

```
> /help
📚 GigaChat AI Agent - Project Information

═══════════════════════════════════════════

📖 Project Overview:
A cross-platform chat application built with Kotlin Compose...

✨ Current Features:
- Git MCP Server (Day 21)
- Clickable Web Sources (Day 19)
...
```

### Будущие команды (примеры)

```
/status          - Show system status
/clear           - Clear conversation history
/model <name>    - Switch AI model
/export          - Export conversation
/config          - Show current configuration
```

## Интеграция с Koin

Команды регистрируются в DI контейнере:

```kotlin
// Command System
single<ProjectRootProvider> { createProjectRootProvider() }
single { CommandHandler(get()) }

// AgentStore with CommandHandler
single {
    AgentStore(
        // ...
        commandHandler = get(),
        // ...
    )
}
```

## UI компоненты

### MessageItem.kt

Добавлена обработка `MessageType.COMMAND`:
- Цвет префикса: зеленый (0xFFA9DC76)
- Цвет текста: зеленый (0xFFA9DC76)
- Отступ снизу: 14dp

### AgentScreen.kt

Добавлена обработка в copy all messages:
```kotlin
MessageType.COMMAND -> ""
```

## Тестирование

### Desktop

```bash
./gradlew :ai-agent:run
# В приложении введите: /help
```

### Android

```bash
./gradlew :ai-agent:installDebug
# В приложении введите: /help
```

## Ограничения

1. **Команды не имеют истории выполнения** - не сохраняются между сессиями
2. **Нет автодополнения команд** - требуется полный ввод
3. **Android имеет ограниченный доступ к файлам** - статический контент для /help
4. **Команды не поддерживают сложные аргументы** - только простой парсинг строк

## Планы на будущее

- [ ] Добавить автодополнение команд
- [ ] История команд (стрелки вверх/вниз)
- [ ] Команды с интерактивными параметрами
- [ ] Команды для управления MCP серверами
- [ ] Команды для RAG настроек
- [ ] Экспорт/импорт сессий
- [ ] Команды для Git операций

## Заметки разработчика

- Команды обрабатываются **до** отправки в AI модель
- Команды **не включаются** в контекст разговора с AI
- Результаты команд **сохраняются** в истории сессии
- Команды работают **независимо** от выбранной AI модели
