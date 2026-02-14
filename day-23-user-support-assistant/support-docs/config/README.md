# Configuration Files for AI Agent

Этот каталог содержит конфигурационные файлы для AI Agent.

## Файлы

### System Prompts

Системные промпты для специализированных режимов AI Agent:

#### `support-assistant-prompt.md`
Системный промпт для режима технической поддержки (`/support` команда).

**Использование:**
```bash
./gradlew :ai-agent:run
# В AI Agent:
/support [вопрос пользователя]
```

**Функции:**
- Интеграция с RAG (поиск в базе знаний)
- Доступ к CRM инструментам через MCP
- Персонализированные ответы с учетом истории тикетов

#### `review-pr-prompt.md`
Системный промпт для режима code review (`/review-pr` команда).

**Использование:**
```bash
./gradlew :ai-agent:run
# В AI Agent:
/review-pr [номер PR | название ветки]
/review-pr 123        # Review PR #123
/review-pr feature/x  # Review ветки feature/x
/review-pr            # Review текущих изменений
```

**Функции:**
- Проверка критических багов (division by zero, memory leaks, etc.)
- Технологически-специфичные чеклисты (Kotlin, Python, SQL, etc.)
- Анти-галлюцинационные правила
- Обязательные номера строк в замечаниях

**Архитектура:**
Промпт содержит два placeholder-а для динамического контента:
- `{CRITICAL_BUGS_INSTRUCTIONS}` - критические баги (buildCriticalBugsInstructions())
- `{TECHNOLOGY_BASED_INSTRUCTIONS}` - технологические чеклисты (buildTechnologyBasedInstructions())

Эти placeholder-ы заменяются в runtime в методе `buildSimplifiedReviewInstructions()`.

### MCP Configuration

#### `mcp-config.json`
Конфигурация MCP серверов для подключения к внешним инструментам.

**Поддерживаемые MCP серверы:**
- Git MCP Server (репозитории, коммиты, diff, PR)
- CRM Mock Server (пользователи, тикеты, для demo)

## Редактирование Промптов

### Support Assistant Prompt

1. Откройте `support-assistant-prompt.md`
2. Измените содержимое между ` ``` ` markers
3. Перезапустите AI Agent
4. Выполните `/support` для проверки

### Review PR Prompt

1. Откройте `review-pr-prompt.md`
2. Измените содержимое между ` ``` ` markers
3. **НЕ изменяйте** placeholder-ы `{CRITICAL_BUGS_INSTRUCTIONS}` и `{TECHNOLOGY_BASED_INSTRUCTIONS}`
4. Для изменения критических багов отредактируйте `buildCriticalBugsInstructions()` в `CommandHandler.kt`
5. Для изменения технологических чеклистов отредактируйте `SpecializedChecklists.kt`
6. Перезапустите AI Agent
7. Выполните `/review-pr` для проверки

## Fallback Режим

Если файл промпта не найден, AI Agent автоматически переключится на fallback режим с встроенным (legacy) промптом из кода. Это обеспечивает backward compatibility.

## Структура Кода

**Загрузка промптов:**
```kotlin
// CommandHandler.kt

// Support Assistant
private suspend fun loadSupportAssistantPrompt(): String?

// Review PR
private suspend fun loadReviewPrPromptTemplate(): String?
```

**Использование:**
```kotlin
// Support
private suspend fun handleSupportCommand(args: String?): CommandResult {
    val systemPrompt = loadSupportAssistantPrompt() ?: return error
    // ...
}

// Review PR
private suspend fun buildSimplifiedReviewInstructions(
    detectedTechnologies: Set<String>
): String {
    val template = loadReviewPrPromptTemplate()
    return template
        ?.replace("{CRITICAL_BUGS_INSTRUCTIONS}", buildCriticalBugsInstructions())
        ?.replace("{TECHNOLOGY_BASED_INSTRUCTIONS}", buildTechnologyBasedInstructions(detectedTechnologies))
        ?: fallbackPrompt()
}
```

## Преимущества Markdown Промптов

1. **Легкое редактирование** - не нужно перекомпилировать код
2. **Версионирование** - промпты в git, видна история изменений
3. **A/B тестирование** - легко создать варианты промптов
4. **Разделение ответственности** - промпт-инженеры могут работать независимо
5. **Быстрые итерации** - изменения вступают в силу сразу после перезапуска
6. **Fallback** - если файл не найден, используется legacy версия из кода

## История Изменений

**2026-02-14:**
- Добавлен `review-pr-prompt.md` - перенос системного промпта для code review из кода в отдельный файл
- Обновлен `CommandHandler.kt` - добавлен метод `loadReviewPrPromptTemplate()`
- Сохранен fallback режим для backward compatibility
