# Тестирование отображения источников в режиме /support

## Быстрая проверка (5 минут)

### Шаг 1: Запуск

```bash
# Терминал 1: Запустить CRM MCP Server
cd mcp-servers
./START.sh

# Терминал 2: Запустить AI Agent
./gradlew :ai-agent:run
```

### Шаг 2: Тестовый запрос

В UI AI Agent введите:

```
/support Нет напоминаний в Telegram
```

### Шаг 3: Проверка результата

**✅ Что должно быть:**

1. **Ответ AI содержит маркеры источников:**
   ```
   Согласно документации [Источник 1] и тикету ticket_007 [Источник 2], ...
   ```

2. **Раздел "Источники" отображается и содержит:**
   ```
   Источники (2) ▼

   [1] LOCAL_TOOLS.md
     "Telegram reminders require send_telegram_reminder..."
     Фрагмент 3/8 · Релевантность: 94%

   [2] ticket_007
     "Не приходят напоминания в Telegram [in_progress]
     Приоритет: medium | Категория: Integration
     Настроил Telegram Bot Token и Chat ID, но..."
     Релевантность: 85%
   ```

3. **В консоли логи:**
   ```
   [AgentStore] Combined sources: RAG=1, CRM=1, Total=2
   [AgentStore] Attached 2 sources to AI response
   ```

**❌ Чего НЕ должно быть:**

- Пустой раздел "Источники"
- Маркеры `[Источник N]` без соответствующих источников
- Ошибки парсинга в консоли

## Расширенное тестирование

### Тест 1: RAG + CRM (оба типа источников)

**Запрос:**
```
/support Нет напоминаний в Telegram
```

**Ожидается:**
- RAG источники (документация о Telegram)
- CRM источники (тикеты ticket_007 и т.д.)
- Правильная нумерация: [Источник 1], [Источник 2], ...

### Тест 2: Только CRM (RAG отключен)

**Подготовка:**
1. Settings → RAG → Отключить RAG mode

**Запрос:**
```
/support ticket_007
```

**Ожидается:**
- Источник [1] ticket_007
- Цитата с темой, статусом, приоритетом, описанием

### Тест 3: Только RAG (похожих тикетов нет)

**Запрос:**
```
/support Что такое MCP?
```

**Ожидается:**
- Источники из документации (README.md, docs/MCP_*.md)
- В ответе: "Похожих тикетов не найдено"
- Нет CRM источников в разделе "Источники"

### Тест 4: Множественные тикеты

**Запрос:**
```
/support Проблемы с авторизацией
```

**Ожидается:**
- search_tickets вернет несколько тикетов
- Все тикеты отображаются как источники
- Нумерация последовательная

### Тест 5: get_user_tickets

**Запрос:**
```
/support Тикеты пользователя user_002
```

**Ожидается:**
- AI вызовет get_user_tickets
- Все тикеты пользователя отображаются как источники

## Проверка парсинга

### Тест парсера search_tickets

**Создайте файл:** `test_ticket_parser.kt`

```kotlin
fun main() {
    val mockResult = """
        Найдено тикетов: 2 по запросу 'напоминания telegram'
        (Отсортировано по релевантности)

        1. ticket_007: Не приходят напоминания в Telegram [in_progress]
           Приоритет: medium | Категория: Integration
           Описание: Настроил Telegram Bot Token и Chat ID, но напоминания не приходят...
           📊 Релевантность: 8.5 (Matched terms: напоминание*, telegram)

        2. ticket_003: Telegram integration issue [resolved]
           Приоритет: high | Категория: Integration
           Описание: Bot не отвечает на команды...
           📊 Релевантность: 6.2 (Matched terms: telegram)
    """.trimIndent()

    val sources = TicketSourceParser.parseSearchTicketsSources(mockResult, 1)

    println("Parsed ${sources.size} sources:")
    sources.forEach { (index, source) ->
        println("[$index] ${source.filePath}")
        println("    Similarity: ${source.similarity}")
        println("    Text: ${source.text.take(100)}...")
        println()
    }
}
```

**Ожидаемый вывод:**
```
Parsed 2 sources:
[1] ticket_007
    Similarity: 0.85
    Text: Не приходят напоминания в Telegram [in_progress]
    Приоритет: medium | Категория: Integration...

[2] ticket_003
    Similarity: 0.62
    Text: Telegram integration issue [resolved]
    Приоритет: high | Категория: Integration...
```

## Отладка проблем

### Проблема: Источники не отображаются

**Проверка 1: RAG источники**
```
Лог: [AgentStore] Combined sources: RAG=0, CRM=0, Total=0
```
→ RAG не настроен или индекс пустой
→ Решение: включить RAG mode и указать путь к индексу

**Проверка 2: CRM источники**
```
Лог: [TicketSourceParser] Parsed 0 tickets from search_tickets result
```
→ Парсер не нашел тикеты в результате
→ Решение: проверить формат вывода search_tickets

**Проверка 3: Tool не вызван**
```
Лог: [AgentStore] Phase 1 completed, no tool calls detected
```
→ AI не вызвал search_tickets
→ Решение: проверить системный промпт SUPPORT_ASSISTANT_PROMPT

### Проблема: Неправильная нумерация

**Симптом:**
```
Ответ: [Источник 1] ... [Источник 3]
Источники: [1] doc.md, [2] ticket_007
```

**Причина:** startIndex не учитывает RAG источники

**Решение:**
```kotlin
val ticketSources = extractTicketSources(
    toolResponses,
    startIndex = (phase1Sources?.size ?: 0) + 1  // ✅ Правильно
)
```

### Проблема: Пустые цитаты

**Симптом:**
```
[2] ticket_007
    ""
    Релевантность: 85%
```

**Причина:** Regex в парсере не соответствует формату

**Решение:** Проверить patterns в `TicketSourceParser.kt`

## Логи для проверки

### Успешное выполнение

```
[SearchService] Query expanded: 'напоминания telegram' → 6 terms
[SearchService] Found 2 tickets with scores
[CRM] Tool search_tickets [OK]:
1. ticket_007: Не приходят напоминания в Telegram [in_progress]
...

[AgentStore] Phase 1: Loading RAG context
[RagRepository] Found 3 relevant chunk(s)
[AgentStore] RAG context loaded, 3 sources

[AgentStore] Phase 1: Executing tools
[ToolExecutor] Executing tool: search_tickets
[AgentStore] Tool results received

[TicketSourceParser] Parsed 2 tickets from search_tickets result
[AgentStore] Combined sources: RAG=3, CRM=2, Total=5
[AgentStore] Attached 5 sources to AI response
```

### Ошибка парсинга

```
[TicketSourceParser] WARNING: Could not parse ticket line: "..."
[TicketSourceParser] Parsed 0 tickets from search_tickets result
[AgentStore] Combined sources: RAG=3, CRM=0, Total=3
```

## Чеклист перед коммитом

- [ ] AI Agent запускается без ошибок
- [ ] CRM MCP Server подключен (Settings → MCP)
- [ ] RAG mode включен и индекс загружен
- [ ] Команда `/support` работает
- [ ] Раздел "Источники" отображается
- [ ] RAG источники присутствуют (если настроен RAG)
- [ ] CRM источники присутствуют (если есть похожие тикеты)
- [ ] Нумерация источников правильная и последовательная
- [ ] Клик по источнику раскрывает цитату
- [ ] Цитаты содержат корректную информацию
- [ ] Логи не содержат ошибок парсинга

## Сборка и проверка

```bash
# Полная пересборка
./gradlew clean build

# Запуск тестов (если добавлены)
./gradlew :ai-agent:test

# Запуск приложения
./gradlew :ai-agent:run
```

## Полезные команды

### Просмотр логов CRM Server

```bash
cd mcp-servers
tail -f crm.log
```

### Тестирование CRM Server напрямую

```bash
cd mcp-servers
python test_search_direct.py
```

### Проверка индекса RAG

```bash
./gradlew :shared:runIndexing --args="stats ./index.json"
```

## Результаты тестирования

**Отметьте выполненные тесты:**

- [ ] Тест 1: RAG + CRM
- [ ] Тест 2: Только CRM
- [ ] Тест 3: Только RAG
- [ ] Тест 4: Множественные тикеты
- [ ] Тест 5: get_user_tickets
- [ ] Парсер search_tickets
- [ ] Парсер get_ticket
- [ ] Парсер get_user_tickets

**Найденные проблемы:**

(Запишите здесь любые найденные проблемы)

## См. также

- [SUPPORT_SOURCES_GUIDE.md](SUPPORT_SOURCES_GUIDE.md) — полное руководство
- [CHANGELOG_SUPPORT_SOURCES.md](CHANGELOG_SUPPORT_SOURCES.md) — описание изменений
- [MANUAL_TEST_INSTRUCTIONS.md](MANUAL_TEST_INSTRUCTIONS.md) — ручное тестирование поддержки
